package br.unifesp.fisicamedica;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import ij.gui.OvalRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.measure.Calibration;
import ij.plugin.filter.EDM;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

public class Segmenter {
	 public static PolygonRoi segmenter(ImageProcessor ip, Calibration cal, double minHU, double minArea, double minCircularity, boolean useManager, int slicenumber, int radius) {
	     PolygonRoi largerRoi = null;
	        double largerArea = 0;
	        ByteProcessor Mask = new ByteProcessor(ip.getWidth(), ip.getHeight());
	        for (int y = 0; y < ip.getHeight(); y++) {
	            for (int x = 0; x < ip.getWidth(); x++) {
	            	double valor = ip.getValue(x, y);
	                if (valor >= minHU) {
	                    Mask.putPixel(x, y, 255);
	                } else {
	                    Mask.putPixel(x, y, 0);
	                }
	            }
	        }
	      ByteProcessor filledMask = fillHoles(Mask);
	      ByteProcessor erodedMask = filledMask;
	      if(radius!=0) {
	    	  erodedMask = erode(filledMask, radius);
	      }
	        Wand wand = new Wand(erodedMask);

	        for (int y = 0; y < ip.getHeight(); y++) {
	            for (int x = 0; x < ip.getWidth(); x++) {
	                if (erodedMask.getPixel(x, y) == 255) {
	                    wand.autoOutline(x, y, 255, 255, 8 | 256);
	                    if (wand.npoints > 0) {
	                        PolygonRoi erodedRoi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, Roi.FREEROI);
	                        PolygonRoi dilatedRoi = erodedRoi;
	                        if(radius!=0) {
	                     	   dilatedRoi = dilate(filledMask, erodedRoi, radius);
	                        }
	                        
	                        ip.setRoi(dilatedRoi);
	                        ImageStatistics stats = ImageStatistics.getStatistics(ip, 0, cal);
	                        double area = stats.area;
	                        double perimeter = dilatedRoi.getLength();
	                        double circularity = perimeter == 0.0 ? 0.0 : 4.0 * Math.PI * (stats.pixelCount / (perimeter * perimeter));
	                        if (area >= minArea && circularity >= minCircularity) {
	                            if (area > largerArea) {
	                                largerArea = area;
	                                largerRoi = dilatedRoi;
	                            }
	                        }
	                        ip.resetRoi();
	                        erodedMask.setValue(0);
	                        erodedMask.fill(erodedRoi);
	                    }
	                }
	            }
	        }
	        if(largerRoi!=null) {

	       PolygonRoi finalRoi = largerRoi;
	       
	        ip.setRoi(finalRoi);
	        
	        if (useManager) {
	            RoiManager roiManager = RoiManager.getInstance();
	            if (roiManager == null) {
	                roiManager = new RoiManager();
	            }
	           finalRoi.setPosition(slicenumber);
	           roiManager.addRoi(finalRoi);
	        }
            ip.resetRoi();

	        return finalRoi;
	        }else {
	        	return null;
	        }
	    }
	    
	    public static ByteProcessor erode(ByteProcessor mask, int radius) {
	        ByteProcessor erodedMask = new ByteProcessor(mask.getWidth(), mask.getHeight());
	        FloatProcessor floatEdm = new EDM().makeFloatEDM(mask, 0, true);
	        float[] fPixels = (float[]) floatEdm.getPixels();
	        int width = erodedMask.getWidth();
	        int height = erodedMask.getHeight();
	        for (int y = 0; y < height; y++) {
	        	 for (int x = 0, p = x + y * width; x < width; x++, p++) {
	        	        if (fPixels[p] > radius) {
	                    erodedMask.putPixel(x, y, 255);
	                }else {
	                	erodedMask.putPixel(x, y, 0);
	                }
	            }
	        }
	        return erodedMask;
	    }
	    
	    public static PolygonRoi dilate(ByteProcessor mask, Roi roi, int radius) {
	    	 ByteProcessor dilatedMask = new ByteProcessor(mask.getWidth(), mask.getHeight());
		        dilatedMask.setColor(255); dilatedMask.fill();
		        dilatedMask.setColor(0);dilatedMask.fill(roi);
		      
		        FloatProcessor edm = new EDM().makeFloatEDM(dilatedMask.duplicate(), 0, false);
		        float[] edmPixels = (float[]) edm.getPixels();
		        byte[] dilatedPixels = (byte[]) dilatedMask.getPixels();
		        byte[] originalPixels = (byte[]) mask.getPixels();
		        
		        for (int y = 0; y < mask.getHeight(); y++) {
		            for (int x = 0; x < mask.getWidth(); x++) {
		                int index = y * mask.getWidth() + x;
		                if (edmPixels[index] <= radius && originalPixels[index] == (byte) 255) {
		                    dilatedPixels[index] = (byte) 0;
		                }
		            }
		        }
		        
		        Wand wand = new Wand(dilatedMask);
		        for (int y = 0; y < dilatedMask.getHeight(); y++) {
		            for (int x = 0; x < dilatedMask.getWidth(); x++) {
		                if (dilatedMask.getPixel(x, y) == 0) {
		                    wand.autoOutline(x, y, 0, 0, 8 | 256);
		                    if (wand.npoints > 0) {
		                         roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, Roi.FREEROI);
		              	       return (PolygonRoi) roi;
		                    }
		                }
		            }
		        }
		        return null;
	    }
	    
	    public static ByteProcessor fillHoles(ByteProcessor ip) {
	        ByteProcessor filledIp = (ByteProcessor) ip.duplicate();

	        int width = filledIp.getWidth();
	        int height = filledIp.getHeight();
	        int background = 0;
	        int foreground = 255;
	        int tempValue = 127; 
	        
	        FloodFiller ff = new FloodFiller(filledIp);
	        filledIp.setColor(tempValue);

	        for (int x = 0; x < width; x++) {
	            if (filledIp.getPixel(x, 0) == background) ff.fill(x, 0);
	            if (filledIp.getPixel(x, height - 1) == background) ff.fill(x, height - 1);
	        }
	        for (int y = 0; y < height; y++) {
	            if (filledIp.getPixel(0, y) == background) ff.fill(0, y);
	            if (filledIp.getPixel(width - 1, y) == background) ff.fill(width - 1, y);
	        }
	        for (int y = 0; y < height; y++) {
	            for (int x = 0; x < width; x++) {
	                int pixel = filledIp.getPixel(x, y);
	                if (pixel == tempValue) {
	                    filledIp.putPixel(x, y, background);
	                } else if (pixel == background) {
	                    filledIp.putPixel(x, y, foreground);
	                }
	            }
	        }
	        return filledIp;
	    }

    
    public static class DetectorResult {
    	List<Double> HU = new ArrayList<>();
    	List<Double> StdDev = new ArrayList<>();

    	List<Double> Threshold = new ArrayList<>();
    	List<Double> radius = new ArrayList<>();
    	List<Roi> finalRoi = new ArrayList<>();

    	List<Point> Pos = new ArrayList<>();
    }

            private static boolean isRegionProcessed(ByteProcessor mask, int x, int y, double windowSizePx) {
        	for(int i=x; i<x+windowSizePx; i++) {
        		for(int j=y; j<y+windowSizePx; j++) {
        			if (mask.getPixel(i, j)==255) return true;
        		}
        	}
            return false;
        }

        public static OtsuResult Otsu(int [] data ) {
    		// Otsu's threshold algorithm
    		// M. Emre Celebi 6.15.2007, Fourier Library https://sourceforge.net/projects/fourier-ipal/
    		// ported to ImageJ plugin by G.Landini
    		
    		int ih;
    		int threshold=-1;
    		int num_pixels=0;
    		double total_mean;	/* mean gray-level for the whole image */
    		double bcv, term;	/* between-class variance, scaling term */
    		double max_bcv;		/* max BCV */
    		double [] cnh = new  double [data.length];	/* cumulative normalized histogram */
    		double [] mean = new  double [data.length]; /* mean gray-level */
    		double [] histo = new  double [data.length];/* normalized histogram */

    		/* Calculate total number of pixels */
    		for ( ih = 0; ih < data.length; ih++ )
    			num_pixels=num_pixels+data[ih];
    	
    		term = 1.0 / ( double ) num_pixels;

    		/* Calculate the normalized histogram */
    		for ( ih = 0; ih < data.length; ih++ ) {
    			histo[ih] = term * data[ih];
    		}

    		/* Calculate the cumulative normalized histogram */
    		cnh[0] = histo[0];
    		for ( ih = 1; ih < data.length; ih++ ) {
    			cnh[ih] = cnh[ih - 1] + histo[ih];
    		}

    		mean[0] = 0.0;

    		for ( ih = 0 + 1; ih <data.length; ih++ ) {
    			mean[ih] = mean[ih - 1] + ih * histo[ih];
    		}

    		total_mean = mean[data.length-1];

    		//	Calculate the BCV at each gray-level and find the threshold that maximizes it 
    		threshold = Integer.MIN_VALUE;
    		max_bcv = 0.0;

    		for ( ih = 0; ih < data.length; ih++ ) {
    			bcv = total_mean * cnh[ih] - mean[ih];
    			bcv *= bcv / ( cnh[ih] * ( 1.0 - cnh[ih] ) );

    			if ( max_bcv < bcv ) {
    				max_bcv = bcv;
    				threshold = ih;
    			}
    		}
    		OtsuResult or = new OtsuResult();
    		or.max_bcv=max_bcv;
    		or.threhsold=threshold;
    		return or;
    	}
        

        public static DetectorResult localOtsuDetector(ImageProcessor ipOrig, Calibration cal, double windowSizePx, double overlap, int sliceNumber, boolean useManager) {
            DetectorResult result = new DetectorResult();

            ImageProcessor ip = ipOrig.duplicate();
            ip.blurGaussian(2.0); // Blur para suavizar ruído antes do gradiente

            // Mapa de gradiente global (usado depois para refinar a borda)
            FloatProcessor gradMag = calcularGradienteMagnitude(ip);

            ImageStatistics stats2 = ip.getStatistics();
            double mode = (stats2.histMin + stats2.mode * stats2.binSize);
            
            // Passo da janela deslizante
            int step = (int) Math.max(1, Math.round(windowSizePx * (1 - overlap)));
            
            // Máscara para evitar processar a mesma região duas vezes
            ByteProcessor mask = new ByteProcessor(ip.getWidth(), ip.getHeight());
            mask.setColor(0); mask.fill();

            // Loop da Janela Deslizante
            for (int y = 0; y <= ip.getHeight() - windowSizePx; y += step) {
                for (int x = 0; x <= ip.getWidth() - windowSizePx; x += step) {
                    
                    // Se o centro da janela já foi processado, pula
                    if (isRegionProcessed(mask, x, y, windowSizePx)) continue;

                    // 1. Recorta a Janela
                    ip.setRoi(new Roi(x, y, windowSizePx, windowSizePx));
                    ImageProcessor window = ip.crop();
                    ip.resetRoi(); // Reseta ROI da imagem principal

                    // 2. Calcula Estatísticas LOCAIS (dentro da janela)
                    ImageStatistics stats = window.getStatistics();
                    OtsuResult or = Otsu(stats.histogram);
                    
                    // Se a variância entre classes for baixa, provavelmente é fundo ou região homogênea
                    if (or.max_bcv < 1000) continue; // Ajuste esse valor se necessário

                    double threshold = (stats.histMin + or.threhsold * stats.binSize + stats.binSize / 2.0);

                    // Define min/max HU para a Wand baseado no limiar
                    double minHU, maxHU;
                    if (threshold >= mode) { minHU = threshold; maxHU = Double.MAX_VALUE; }
                    else { minHU = -Double.MAX_VALUE; maxHU = threshold; }

                    // --- CORREÇÃO AQUI ---
                    // Usa a Wand NA JANELA (window), não na imagem original (ipOrig)
                    // Isso impede que a seleção "vaze" para fora da caixa de 50x50px
                    Wand wand = new Wand(window);
                    boolean found = false;
                    
                    // Varre a pequena janela procurando o objeto
                    for (int i = 0; i < window.getWidth() && !found; i++) {
                        for (int j = 0; j < window.getHeight() && !found; j++) {
                            double val = window.getValue(i, j);
                            
                            if (val > minHU && val < maxHU) {
                                // Tenta delinear na coordenada LOCAL (i, j)
                                wand.autoOutline(i, j, (int)Math.round(minHU), (int)Math.round(Math.min(maxHU, 65535)), 8 | 256);
                                
                                if (wand.npoints > 0) {
                                    found = true;
                                    // Encontramos algo dentro da janela!
                                    
                                    // 3. Traduzir Coordenadas LOCAIS para GLOBAIS
                                    int[] globalX = new int[wand.npoints];
                                    int[] globalY = new int[wand.npoints];
                                    
                                    double sumX = 0, sumY = 0;
                                    for(int p=0; p<wand.npoints; p++) {
                                        globalX[p] = wand.xpoints[p] + x; // Soma offset X da janela
                                        globalY[p] = wand.ypoints[p] + y; // Soma offset Y da janela
                                        sumX += globalX[p];
                                        sumY += globalY[p];
                                    }
                                    
                                    // Cria ROI temporária nas coordenadas globais
                                    PolygonRoi roiGlobal = new PolygonRoi(globalX, globalY, wand.npoints, Roi.FREEROI);
                                    
                                    double centerX = sumX / wand.npoints;
                                    double centerY = sumY / wand.npoints;
                                    double raioAprox = Math.max(roiGlobal.getBounds().width, roiGlobal.getBounds().height) / 2.0;

                                    // Filtros de tamanho (converter px para mm se possível, ou usar valor fixo razoável)
                                    // Inserts do ACR têm ~25mm diâmetro -> raio ~12.5mm
                                    // Tolerância: entre 5mm e 20mm
                                    double pixelSize = cal.pixelWidth > 0 ? cal.pixelWidth : 1.0;
                                    double minR = 5.0 / pixelSize; 
                                    double maxR = 20.0 / pixelSize;
                                    
                                    if (raioAprox < minR || raioAprox > maxR) continue; 

                                    // 4. Refinamento por Gradiente (nas coordenadas globais)
                                    FloatPolygon gradBorder = detectarBordaPorGradiente(gradMag, centerX, centerY, raioAprox);

                                    if (gradBorder == null || gradBorder.npoints < 6) continue;

                                    // Transforma em círculo perfeito
                                    FloatPolygon oval = ovalPolygon(gradBorder);
                                    
                                    // Adiciona aos resultados
                                    result.Pos.add(new Point((int)Math.round(centerX), (int)Math.round(centerY)));
                                    
                                    // Mede HU e StdDev na imagem original usando a ROI refinada
                                    PolygonRoi finalPolyRoi = new PolygonRoi(oval, Roi.POLYGON);
                                    ipOrig.setRoi(finalPolyRoi);
                                    ImageStatistics stats1 = ipOrig.getStatistics();
                                    
                                    result.HU.add(stats1.mean);
                                    result.StdDev.add(stats1.stdDev);
                                    result.Threshold.add(threshold);
                                    result.radius.add(raioAprox);
                                    result.finalRoi.add(finalPolyRoi);
                                    
                                    // Adiciona ao Manager para visualização
                                    if (useManager) {
                                        RoiManager roiManager = RoiManager.getInstance();
                                        if (roiManager == null) roiManager = new RoiManager();
                                        
                                        // Importante clonar ou criar nova instância para garantir posição correta
                                        PolygonRoi managerRoi = (PolygonRoi) finalPolyRoi.clone();
                                        managerRoi.setPosition(sliceNumber);
                                        managerRoi.setName("Otsu_" + (int)stats1.mean); // Nomeia com HU para facilitar debug
                                        roiManager.addRoi(managerRoi);
                                    }

                                    // Marca na máscara global para não processar vizinhos imediatos
                                    // Usa ROI circular um pouco maior para cobrir o insert
                                    mask.setColor(255);
                                    mask.fill(new OvalRoi(centerX - raioAprox*1.2, centerY - raioAprox*1.2, 2.4 * raioAprox, 2.4 * raioAprox));
                                }
                            }
                        }
                    }
                }
            }
            return result;
        }
        private static FloatProcessor calcularGradienteMagnitude(ImageProcessor ip) {
            int w = ip.getWidth(), h = ip.getHeight();
            FloatProcessor grad = new FloatProcessor(w, h);
            // usar kernel Sobel padrão (assegura float ops)
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    double gx =
                        -1 * ip.getPixelValue(x - 1, y - 1) + 1 * ip.getPixelValue(x + 1, y - 1) +
                        -2 * ip.getPixelValue(x - 1, y    ) + 2 * ip.getPixelValue(x + 1, y    ) +
                        -1 * ip.getPixelValue(x - 1, y + 1) + 1 * ip.getPixelValue(x + 1, y + 1);
                    double gy =
                        -1 * ip.getPixelValue(x - 1, y - 1) + -2 * ip.getPixelValue(x, y - 1) + -1 * ip.getPixelValue(x + 1, y - 1) +
                         1 * ip.getPixelValue(x - 1, y + 1) +  2 * ip.getPixelValue(x, y + 1) +  1 * ip.getPixelValue(x + 1, y + 1);
                    grad.setf(x, y, (float) Math.sqrt(gx * gx + gy * gy));
                }
            }
            return grad;
        }

        private static FloatPolygon detectarBordaPorGradiente(FloatProcessor gradMag, double cx, double cy, double rAprox) {
            FloatPolygon border = new FloatPolygon();
            int nSteps = (int) Math.ceil(2.0 * Math.PI * Math.max(3.0, rAprox));
            nSteps = Math.max(48, Math.min(512, nSteps)); // limites

            double rMin = Math.max(1.0, rAprox * 0.7);
            double rMax = rAprox * 1.3;
            double rStep = Math.max(0.25, (rMax - rMin) / 24.0);

            for (int i = 0; i < nSteps; i++) {
                double theta = 2 * Math.PI * i / nSteps;
                double cosT = Math.cos(theta), sinT = Math.sin(theta);

                double maxVal = -1.0;
                double xMax = cx, yMax = cy;

                for (double r = rMin; r <= rMax; r += rStep) {
                    double x = cx + r * cosT;
                    double y = cy + r * sinT;
                    if (x < 1 || y < 1 || x >= gradMag.getWidth() - 1 || y >= gradMag.getHeight() - 1) continue;
                    double val = gradMag.getInterpolatedValue(x, y);
                    if (val > maxVal) {
                        maxVal = val;
                        xMax = x; yMax = y;
                    }
                }
                border.addPoint((float) xMax, (float) yMax);
            }

            return border;
        }

        private static FloatPolygon ovalPolygon(FloatPolygon src) {
            // 1. Calcular o Centróide
            double sumX = 0;
            double sumY = 0;
            int n = src.npoints;
            
            for (int i = 0; i < n; i++) {
                sumX += src.xpoints[i];
                sumY += src.ypoints[i];
            }
            double cx = sumX / n;
            double cy = sumY / n;

            // 2. Calcular o Raio Médio
            double sumR = 0;
            for (int i = 0; i < n; i++) {
                double dx = src.xpoints[i] - cx;
                double dy = src.ypoints[i] - cy;
                sumR += Math.sqrt(dx * dx + dy * dy);
            }
            double avgRadius = sumR / n;

            // 3. Gerar um polígono circular suave
            FloatPolygon oval = new FloatPolygon();
            int resolution = 64; // Pontos para desenhar o círculo
            for (int i = 0; i < resolution; i++) {
                double theta = 2.0 * Math.PI * i / resolution;
                float x = (float) (cx + avgRadius * Math.cos(theta));
                float y = (float) (cy + avgRadius * Math.sin(theta));
                oval.addPoint(x, y);
            }
            
            return oval;
        }

        private static class OtsuResult{
    		double threhsold;
    		double max_bcv;
    	}
        
    }