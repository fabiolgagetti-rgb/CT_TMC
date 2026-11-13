package br.unifesp.fisicamedica;

import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;
import ij.process.ImageStatistics;

public class AcrAnalyser {

    public static class AcrLocationResult {
        public int sliceIndex = -1;       // Índice da fatia escolhida (melhor fatia)
        public double rotationAngle = 0;  // Angulo de rotação
        public Point center = new Point(0,0); 
        public boolean found = false;
        
        // Dados dos inserts identificados na MELHOR fatia
        public Point bonePos, airPos, polyPos, acrylicPos;
        
        // Metadados extras
        public int firstModuleSlice = -1;
        public int lastModuleSlice = -1;
    }

    private static class InsertInfo {
        int index; // indice original na lista de detecção
        double hu;
        Point pos;
        
        public InsertInfo(int index, double hu, Point pos) {
            this.index = index;
            this.hu = hu;
            this.pos = pos;
        }
    }
    
    // Classe interna para armazenar dados temporários durante o loop
    private static class SliceCandidate {
        int sliceIndex;
        List<InsertInfo> inserts;
        
        public SliceCandidate(int sliceIndex, List<InsertInfo> inserts) {
            this.sliceIndex = sliceIndex;
            this.inserts = inserts;
        }
    }

    public static AcrLocationResult findPhantomPosition(SeriesData seriesData) {
        AcrLocationResult finalResult = new AcrLocationResult();
        List<File> files = seriesData.getFiles();
        
        // Limpar ROI Manager antes de começar para não misturar análises antigas
        RoiManager rm = RoiManager.getInstance();
        if (rm != null) rm.reset();

        // Listas para guardar quais fatias parecem ser o Módulo 1
        List<SliceCandidate> candidates = new ArrayList<>();

        // Parâmetros de busca Otsu
        double windowSizePx = 30; 
        double overlap = 0.5;

        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            ImagePlus imp = new ImagePlus(file.getAbsolutePath());
            
            if (imp == null || imp.getWidth() == 0) continue;
            
            Calibration cal = imp.getCalibration();
            if (cal.pixelWidth > 0) {
                windowSizePx = 30.0 / cal.pixelWidth;
            }

            // 1. DETECÇÃO DO PHANTOM
            // Parâmetros definidos: HU -400, Area 2000, Circularidade 0.8, Radius 2, UseManager true
            PolygonRoi phantomBody = Segmenter.segmenter(
                imp.getProcessor(), 
                cal, 
                -400,   // minHU
                2000,   // minArea
                0.8,    // minCircularity
                true,   // useManager (vai adicionar ao ROI Manager)
                i + 1,  // sliceNumber
                2       // radius
            );

            // Se não achou o corpo do phantom nesta fatia, pula para próxima
            if (phantomBody == null) {
                continue; 
            }

            // 2. DETECÇÃO FINA DOS INSERTS (Módulo 1)
            // Executa apenas se o corpo do phantom foi detectado
            Segmenter.DetectorResult result = Segmenter.localOtsuDetector(imp.getProcessor(), cal, windowSizePx, overlap, i, true);

            if (result.Pos.size() == 4) {
                // Organiza os inserts encontrados
                List<InsertInfo> inserts = new ArrayList<>();
                for(int k=0; k<4; k++) {
                    inserts.add(new InsertInfo(k, result.HU.get(k), result.Pos.get(k)));
                }

                // Ordena por HU (Ar -> Poli -> Acrilico -> Osso)
                Collections.sort(inserts, Comparator.comparingDouble(o -> o.hu));

                InsertInfo air = inserts.get(0);
                InsertInfo bone = inserts.get(3);
                
                // Validação de contraste para garantir que não são 4 bolhas de ar ou ruído
                if ((bone.hu - air.hu) > 1000) {
                    candidates.add(new SliceCandidate(i, inserts));
                }
            }
            // Opcional: fechar a imagem para liberar memória se forem muitas fatias
            // imp.close(); 
        }

        // 3. PÓS-PROCESSAMENTO: Selecionar a melhor fatia
        if (candidates.isEmpty()) {
            IJ.log("Nenhuma fatia compatível com o Módulo 1 do ACR foi encontrada.");
            return finalResult;
        }

        // Encontra o intervalo de fatias (assumindo contiguidade ou pegando min/max)
        int minSlice = candidates.get(0).sliceIndex;
        int maxSlice = candidates.get(candidates.size() - 1).sliceIndex;
        
        // A "melhor fatia" é a central para evitar volume parcial
        int middleIndex = candidates.size() / 2;
        SliceCandidate bestCandidate = candidates.get(middleIndex);

        // Preencher resultado final baseado no melhor candidato
        finalResult.found = true;
        finalResult.sliceIndex = bestCandidate.sliceIndex;
        finalResult.firstModuleSlice = minSlice;
        finalResult.lastModuleSlice = maxSlice;

        // Recuperar inserts da melhor fatia
        InsertInfo air = bestCandidate.inserts.get(0);      // Menor HU
        InsertInfo poly = bestCandidate.inserts.get(1);
        InsertInfo acrylic = bestCandidate.inserts.get(2);
        InsertInfo bone = bestCandidate.inserts.get(3);     // Maior HU

        finalResult.airPos = air.pos;
        finalResult.polyPos = poly.pos;
        finalResult.acrylicPos = acrylic.pos;
        finalResult.bonePos = bone.pos;

        // 4. CÁLCULO DE GEOMETRIA (Centro e Rotação)
        
        // Centro Geométrico (Média dos 4 inserts)
        int avgX = (air.pos.x + poly.pos.x + acrylic.pos.x + bone.pos.x) / 4;
        int avgY = (air.pos.y + poly.pos.y + acrylic.pos.y + bone.pos.y) / 4;
        finalResult.center = new Point(avgX, avgY);

        // Rotação
        // Calcula o ângulo do vetor do CENTRO até o OSSO (Bone)
        // No ACR padrão alinhado, o osso costuma estar em posições cardeais (ex: 9h ou 3h)
        double dx = finalResult.bonePos.x - finalResult.center.x;
        double dy = finalResult.bonePos.y - finalResult.center.y;
        double angleRad = Math.atan2(dy, dx);
        finalResult.rotationAngle = Math.toDegrees(angleRad);
        
        return finalResult;
    }
    public static class InsertMeasurement {
        public String material;
        public double meanHU;
        public double stdDev;
        
        public InsertMeasurement(String mat, double mean, double std) {
            this.material = mat;
            this.meanHU = mean;
            this.stdDev = std;
        }
    }

    public static class UniformityResult {
        public int sliceIndex;
        public double centerHU;
        public double topHU, rightHU, bottomHU, leftHU; // 12h, 3h, 6h, 9h
        public double centerStdDev; // Para ver ruído no centro
        public boolean valid;
        
        // Cálculo simples de uniformidade absoluta (Max diff center vs peripheral)
        public double getMaxDifference() {
            double maxDiff = 0;
            maxDiff = Math.max(maxDiff, Math.abs(centerHU - topHU));
            maxDiff = Math.max(maxDiff, Math.abs(centerHU - rightHU));
            maxDiff = Math.max(maxDiff, Math.abs(centerHU - bottomHU));
            maxDiff = Math.max(maxDiff, Math.abs(centerHU - leftHU));
            return maxDiff;
        }
    }

    // --- Métodos de Análise ---

    /**
     * Analisa os inserts detectados no Módulo 1 com ROI reduzido (80%).
     */
    public static List<InsertMeasurement> analyzeModule1Inserts(SeriesData series, AcrLocationResult locResult) {
        List<InsertMeasurement> measurements = new ArrayList<>();
        
        if (!locResult.found) return measurements;

        File file = series.getFiles().get(locResult.sliceIndex);
        ImagePlus imp = new ImagePlus(file.getAbsolutePath());
        Calibration cal = imp.getCalibration();
        
        // Array auxiliar para processar os 4 materiais na ordem correta
        // Ordem baseada na detecção do método findPhantomPosition (Ar, Poli, Acrilico, Osso)
        Point[] positions = {locResult.airPos, locResult.polyPos, locResult.acrylicPos, locResult.bonePos};
        String[] names = {"Air", "Polyethylene", "Acrylic", "Bone"};
        
        // Recupera os ROIs originais do result do Segmenter ou cria novos baseados na posição
        // Aqui vamos recriar círculos baseados no raio médio detectado para garantir suavidade
        // Assumindo um raio médio padrão se não tivermos o raio exato salvo, mas idealmente passamos o ROI.
        
        // Nota: Para precisão, o ideal seria que AcrLocationResult guardasse os ROIs originais.
        // Como simplificação, vou assumir um raio padrão dos inserts do ACR (~12.5mm raio físico) 
        // ou re-detectar rapidinho. Mas melhor: vamos usar o ROI circular baseado no centro detectado.
        
        double radiusMm = 12.0; // Raio aprox do insert útil
        double radiusPx = radiusMm / cal.pixelWidth;
        
        for (int i = 0; i < 4; i++) {
            Point center = positions[i];
            
            // Cria ROI original
            OvalRoi originalRoi = new OvalRoi(center.x - radiusPx, center.y - radiusPx, radiusPx * 2, radiusPx * 2);
            
            // Reduz para 80% do tamanho (escala 0.8)
            Roi shrunkRoi = scaleRoi(originalRoi, 0.8);
            
            imp.setRoi(shrunkRoi);
            ImageStatistics stats = imp.getStatistics();
            
            measurements.add(new InsertMeasurement(names[i], stats.mean, stats.stdDev));
        }
        
        return measurements;
    }

    /**
     * Localiza o Módulo 3 e calcula a uniformidade.
     */
    public static UniformityResult analyzeUniformity(SeriesData series, AcrLocationResult locResult) {
        UniformityResult result = new UniformityResult();
        
        if (!locResult.found) return result;

        List<File> files = series.getFiles();
        File fileMod1 = files.get(locResult.sliceIndex);
        ImagePlus impRef = new ImagePlus(fileMod1.getAbsolutePath());
        double sliceThickness = impRef.getCalibration().pixelDepth; // Espessura/Espaçamento Z em mm
        
        if (sliceThickness == 0) sliceThickness = 1.0; // Evita div por zero

        // Distância Modulo 1 -> Modulo 3 é 80mm (4cm + 4cm)
        int sliceOffset = (int) Math.round(80.0 / sliceThickness);
        
        // Precisamos saber a direção. O ACR geralmente é escaneado de forma que o Modulo 1 
        // está numa extremidade. Vamos testar +offset e -offset e ver qual está dentro do volume.
        // Assumindo ordenação padrão de arquivo, vamos tentar inferir ou usar bounds.
        
        int targetSlice = locResult.sliceIndex + sliceOffset;
        
        // Verificação simples de limite. 
        // Se Modulo 1 for a primeira fatia (index baixo), o Modulo 3 está mais pra frente (+).
        // Se Modulo 1 for a última, Modulo 3 está pra trás (-).
        if (targetSlice >= files.size()) {
            targetSlice = locResult.sliceIndex - sliceOffset;
        }
        
        if (targetSlice < 0 || targetSlice >= files.size()) {
            IJ.log("Erro: Não foi possível localizar a fatia do Módulo 3 (Fora dos limites da série).");
            return result;
        }

        result.sliceIndex = targetSlice;
        
        // Abrir imagem do Módulo 3
        File fileMod3 = files.get(targetSlice);
        ImagePlus impUniformity = new ImagePlus(fileMod3.getAbsolutePath());
        Calibration cal = impUniformity.getCalibration();
        
        // Parâmetros do usuário:
        // Raio ROI = 20mm
        // Distancia da borda = 10mm
        double roiRadiusMm = 20.0;
        double distFromEdgeMm = 10.0;
        
        // Raio do Phantom (assumindo padrão ACR ~20cm diametro = 100mm raio)
        double phantomRadiusMm = 100.0; 
        
        // Distância do CENTRO do phantom até o CENTRO do ROI periférico:
        // D = RaioPhantom - Margem - RaioROI
        // D = 100 - 10 - 20 = 70mm
        double distanceToRoiCenterMm = phantomRadiusMm - distFromEdgeMm - roiRadiusMm;
        
        // Converter para pixels
        double roiRadiusPx = roiRadiusMm / cal.pixelWidth;
        double distPx = distanceToRoiCenterMm / cal.pixelWidth;
        
        Point center = locResult.center; // Usamos o centro detectado no Modulo 1 (assumindo alinhamento)
        
        // Se houve rotação detectada no Modulo 1, precisamos rotacionar as posições dos ROIs periféricos
        double angleRad = Math.toRadians(locResult.rotationAngle);
        
        // ROI Central
        result.centerHU = getMeanValue(impUniformity, center.x, center.y, roiRadiusPx);
        result.centerStdDev = getStdDevValue(impUniformity, center.x, center.y, roiRadiusPx);
        
        // Calcular posições periféricas (Top, Right, Bottom, Left)
        // 0 graus (Direita), 90 (Baixo), 180 (Esquerda), 270 (Cima) - Sistema de coordenadas da tela (Y cresce pra baixo)
        // Mas vamos usar coordenadas relativas simples: (0, -d), (d, 0), (0, d), (-d, 0) e aplicar a rotação do phantom.
        
        Point pTop = rotatePoint(new Point(0, (int)-distPx), angleRad);
        Point pRight = rotatePoint(new Point((int)distPx, 0), angleRad);
        Point pBottom = rotatePoint(new Point(0, (int)distPx), angleRad);
        Point pLeft = rotatePoint(new Point((int)-distPx, 0), angleRad);
        
        // Somar ao centro
        result.topHU = getMeanValue(impUniformity, center.x + pTop.x, center.y + pTop.y, roiRadiusPx);
        result.rightHU = getMeanValue(impUniformity, center.x + pRight.x, center.y + pRight.y, roiRadiusPx);
        result.bottomHU = getMeanValue(impUniformity, center.x + pBottom.x, center.y + pBottom.y, roiRadiusPx);
        result.leftHU = getMeanValue(impUniformity, center.x + pLeft.x, center.y + pLeft.y, roiRadiusPx);
        
        result.valid = true;
        return result;
    }

    // --- Helpers ---

    private static double getMeanValue(ImagePlus imp, int x, int y, double radius) {
        OvalRoi roi = new OvalRoi(x - radius, y - radius, radius * 2, radius * 2);
        imp.setRoi(roi);
        return imp.getStatistics().mean;
    }
    
    private static double getStdDevValue(ImagePlus imp, int x, int y, double radius) {
        OvalRoi roi = new OvalRoi(x - radius, y - radius, radius * 2, radius * 2);
        imp.setRoi(roi);
        return imp.getStatistics().stdDev;
    }

    private static Point rotatePoint(Point p, double angleRad) {
        // Rotação 2D básica
        int xNew = (int) Math.round(p.x * Math.cos(angleRad) - p.y * Math.sin(angleRad));
        int yNew = (int) Math.round(p.x * Math.sin(angleRad) + p.y * Math.cos(angleRad));
        return new Point(xNew, yNew);
    }
    
    /**
     * Redimensiona um ROI mantendo o centro.
     * @param roi ROI original
     * @param scaleFactor Fator de escala (ex: 0.8 para 80%)
     */
    public static PolygonRoi scaleRoi(Roi roi, double scaleFactor) {
        FloatPolygon fp = roi.getFloatPolygon();
        int n = fp.npoints;
        float[] x = new float[n];
        float[] y = new float[n];
        
        // Calcular centroide
        double cx = 0, cy = 0;
        for (int i=0; i<n; i++) {
            cx += fp.xpoints[i];
            cy += fp.ypoints[i];
        }
        cx /= n;
        cy /= n;
        
        // Escalar pontos em relação ao centro
        for (int i=0; i<n; i++) {
            x[i] = (float) (cx + (fp.xpoints[i] - cx) * scaleFactor);
            y[i] = (float) (cy + (fp.ypoints[i] - cy) * scaleFactor);
        }
        
        return new PolygonRoi(x, y, n, Roi.POLYGON);
    }
 // --- Nova Classe de Resultado para Baixo Contraste ---
    public static class LowContrastResult {
        public int sliceIndex;
        public double cnr;          // Contrast to Noise Ratio
        public double signalMean;   // Média do Insert
        public double backgroundMean; // Média do Fundo (Centro)
        public double backgroundStdDev; // Ruído do Fundo
        public boolean valid;
    }

    // --- Novo Método de Análise ---

    /**
     * Analisa o Módulo 2 (Low Contrast Resolution).
     * Localiza o cilindro superior (40mm da borda) e calcula o CNR.
     */
    public static LowContrastResult analyzeLowContrast(SeriesData series, AcrLocationResult locResult) {
        LowContrastResult result = new LowContrastResult();
        
        if (!locResult.found) return result;

        List<File> files = series.getFiles();
        File fileMod1 = files.get(locResult.sliceIndex);
        ImagePlus impRef = new ImagePlus(fileMod1.getAbsolutePath());
        double sliceThickness = impRef.getCalibration().pixelDepth; 
        
        if (sliceThickness == 0) sliceThickness = 1.0; 

        // Distância Modulo 1 -> Modulo 2 é 40mm
        int sliceOffset = (int) Math.round(40.0 / sliceThickness);
        
        // Tenta localizar a fatia (mesma lógica de direção usada no Modulo 3)
        int targetSlice = locResult.sliceIndex + sliceOffset;
        if (targetSlice >= files.size()) {
            targetSlice = locResult.sliceIndex - sliceOffset;
        }
        
        if (targetSlice < 0 || targetSlice >= files.size()) {
            IJ.log("Erro: Módulo 2 fora dos limites da série.");
            return result;
        }

        result.sliceIndex = targetSlice;
        
        // Carrega a imagem do Módulo 2
        File fileMod2 = files.get(targetSlice);
        ImagePlus impLc = new ImagePlus(fileMod2.getAbsolutePath());
        Calibration cal = impLc.getCalibration();
        
        // --- Geometria ---
        // Insert de 25mm de diametro (Raio ~12.5 a 13mm)
        // Vamos usar 13mm como base
        double insertRadiusMm = 13.0;
        
        // Posição: 40mm abaixo da borda superior.
        // Raio do phantom ~100mm. Distância do centro = 60mm.
        double distFromCenterMm = 60.0;
        
        // Converter para pixels
        double roiRadiusPx = insertRadiusMm / cal.pixelWidth;
        double distPx = distFromCenterMm / cal.pixelWidth;
        
        // Calcular ROI Reduzida (80%)
        double effectiveRadiusPx = roiRadiusPx * 0.8;
        
        // --- Coordenadas ---
        Point center = locResult.center;
        
        // Vetor base "Para Cima" (12 horas) é (0, -distância) no sistema de imagem
        // Aplicamos a rotação detectada no Módulo 1
        Point relativePos = new Point(0, (int) -distPx); 
        double angleRad = Math.toRadians(locResult.rotationAngle);
        Point rotatedPos = rotatePoint(relativePos, angleRad);
        
        // Centro do Insert
        int insertX = center.x + rotatedPos.x;
        int insertY = center.y + rotatedPos.y;
        
        // --- Medições ---
        
        // 1. ROI do Insert (Sinal)
        OvalRoi roiSignal = new OvalRoi(insertX - effectiveRadiusPx, insertY - effectiveRadiusPx, 
                                        effectiveRadiusPx * 2, effectiveRadiusPx * 2);
        impLc.setRoi(roiSignal);
        ImageStatistics statsSignal = impLc.getStatistics();
        result.signalMean = statsSignal.mean;
        
        // 2. ROI do Fundo (Centro do Phantom) - Mesmo tamanho
        OvalRoi roiBackground = new OvalRoi(center.x - effectiveRadiusPx, center.y - effectiveRadiusPx, 
                                            effectiveRadiusPx * 2, effectiveRadiusPx * 2);
        impLc.setRoi(roiBackground);
        ImageStatistics statsBg = impLc.getStatistics();
        result.backgroundMean = statsBg.mean;
        result.backgroundStdDev = statsBg.stdDev;
        
        // --- Cálculo do CNR ---
        // CNR = |Signal - Background| / Noise
        if (result.backgroundStdDev > 0) {
            result.cnr = Math.abs(result.signalMean - result.backgroundMean) / result.backgroundStdDev;
        } else {
            result.cnr = 0; // Evita divisão por zero
        }
        
        result.valid = true;
        return result;
    }
}