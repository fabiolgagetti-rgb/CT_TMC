package br.unifesp.fisicamedica;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

import org.jfree.chart.ChartPanel;

import br.unifesp.fisicamedica.AcrAnalyser.AcrLocationResult;
import br.unifesp.fisicamedica.AcrAnalyser.InsertMeasurement;
import br.unifesp.fisicamedica.AcrAnalyser.LowContrastResult;
import br.unifesp.fisicamedica.AcrAnalyser.UniformityResult;
import ij.IJ;

public class Panel {
	private static ChartPanel chartPanel;
    static TmcCalc TmcCalc = new TmcCalc();
    public static JPanel create() {
    	JPanel rightSidePanel = new JPanel(new GridBagLayout());
    	GridBagConstraints gbc = new GridBagConstraints();
    	gbc.insets = new Insets(5, 5, 5, 5);
    	gbc.anchor = GridBagConstraints.NORTH;
    	gbc.fill = GridBagConstraints.HORIZONTAL;
    	
    	JPanel panel = new JPanel(new GridBagLayout());
    	panel.setBorder(BorderFactory.createTitledBorder(""));
    	addComponents(panel);
    	panel.setMinimumSize(new Dimension(250,500));


    	chartPanel = GraphManager.createChartPanel();

    	gbc.gridx = 0;
    	gbc.gridy = 0;

    	gbc.fill = GridBagConstraints.BOTH;
    	rightSidePanel.add(panel, gbc);

    	gbc.gridx = 1;
    	gbc.gridy = 0;

    	gbc.fill = GridBagConstraints.BOTH;
    	chartPanel.setMinimumSize(new Dimension(600, 500));

    	rightSidePanel.add(chartPanel, gbc);

    	return rightSidePanel;
    }

    private static void addComponents(JPanel panel) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JButton calculateButton = new JButton("Calculate");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(calculateButton, gbc);
        JButton acrButton = new JButton("ACR Analysis");
        gbc.gridx = 1; // Coloca ao lado do calculate
        gbc.gridy = 0;
        gbc.weightx = 0.5;
        panel.add(acrButton, gbc);
        
        double[] array = {0};
        TabelaDadosPanel tabelaPanel = new TabelaDadosPanel(array, array, array, array);
        panel.add(tabelaPanel, gbc);
        panel.revalidate();
        panel.repaint();
        
        calculateButton.addActionListener(e -> {
            try {
               TmcCalc.run();
               updateResults();
                  panel.remove(1);
               
               TabelaDadosPanel tabelaPanel2 = new TabelaDadosPanel(TmcCalc.position, TmcCalc.Dw, TmcCalc.mA, TmcCalc.stdev);
               gbc.gridx = 0;
               gbc.gridy = 1;
               gbc.weightx = 1.0;
               gbc.weighty = 1.0;
               gbc.fill = GridBagConstraints.BOTH;
               gbc.gridwidth = GridBagConstraints.REMAINDER;
               
               panel.add(tabelaPanel2, gbc);
               panel.revalidate();
               panel.repaint();
            } catch (NumberFormatException ex) {
                IJ.error("Unable to calculate");
            }
        });
     // --- Ação do Botão ACR ---
        acrButton.addActionListener(e -> {
            // Executa em uma thread separada para não travar a interface
            new Thread(() -> {
                performAcrAnalysis();
            }).start();
        });
       
    }
    private static void performAcrAnalysis() {
        SeriesData activeSeries = ImageOpener.activeSeriesData;

        if (activeSeries == null) {
            IJ.error("No active series selected. Please double click a series in the tree.");
            return;
        }

        IJ.log("==========================================");
        IJ.log("Iniciando Análise Automática ACR Phantom...");
        IJ.log("==========================================");

        // 1. Localização e Geometria (Módulo 1)
        AcrLocationResult location = AcrAnalyser.findPhantomPosition(activeSeries);

        if (!location.found) {
            IJ.log("ERRO: Phantom não localizado ou Módulo 1 não identificado.");
            return;
        }

        IJ.log(">> GEOMETRIA E POSICIONAMENTO");
        IJ.log("Módulo 1 detectado na fatia: " + (location.sliceIndex + 1));
        IJ.log("Rotação aplicada: " + String.format("%.2f", location.rotationAngle) + " graus");
        IJ.log("Centro (x, y): (" + location.center.x + ", " + location.center.y + ")");
        IJ.log("------------------------------------------");

        // 2. Análise dos Inserts (Módulo 1)
        IJ.log(">> MÓDULO 1: PRECISÃO DO NÚMERO DE CT (HU)");
        List<InsertMeasurement> inserts = AcrAnalyser.analyzeModule1Inserts(activeSeries, location);
        
        IJ.log(String.format("%-15s | %-10s | %-10s", "Material", "Mean HU", "StdDev"));
        for (InsertMeasurement m : inserts) {
            IJ.log(String.format("%-15s | %-10.2f | %-10.2f", 
                    m.material, m.meanHU, m.stdDev));
        }
        IJ.log("------------------------------------------");

        // 3. Baixo Contraste (Módulo 2)
        IJ.log(">> MÓDULO 2: RESOLUÇÃO DE BAIXO CONTRASTE");
        LowContrastResult lc = AcrAnalyser.analyzeLowContrast(activeSeries, location);
        
        if (lc.valid) {
            IJ.log("Analisado na fatia: " + (lc.sliceIndex + 1));
            IJ.log("Média Insert (Sinal): " + String.format("%.2f", lc.signalMean));
            IJ.log("Média Fundo: " + String.format("%.2f", lc.backgroundMean));
            IJ.log("Ruído (StdDev Fundo): " + String.format("%.2f", lc.backgroundStdDev));
            IJ.log("CNR (Contrast-to-Noise Ratio): " + String.format("%.2f", lc.cnr));
        } else {
            IJ.log("Falha ao localizar Módulo 2.");
        }
        IJ.log("------------------------------------------");

        // 4. Uniformidade (Módulo 3)
        IJ.log(">> MÓDULO 3: UNIFORMIDADE");
        UniformityResult uni = AcrAnalyser.analyzeUniformity(activeSeries, location);
        
        if (uni.valid) {
            IJ.log("Analisado na fatia: " + (uni.sliceIndex + 1));
            IJ.log("Centro HU: " + String.format("%.2f", uni.centerHU));
            IJ.log("Topo (12h) HU: " + String.format("%.2f", uni.topHU));
            IJ.log("Direita (3h) HU: " + String.format("%.2f", uni.rightHU));
            IJ.log("Baixo (6h) HU: " + String.format("%.2f", uni.bottomHU));
            IJ.log("Esquerda (9h) HU: " + String.format("%.2f", uni.leftHU));
            IJ.log("Maior Diferença (Centro vs Perif): " + String.format("%.2f", uni.getMaxDifference()));
        } else {
            IJ.log("Falha ao localizar Módulo 3.");
        }
        IJ.log("==========================================");
        IJ.log("Análise Concluída.");
    }
    
    private static void updateResults() {
   
      GraphManager.updateChart(TmcCalc);

    }
    public static String resultToString(double result, boolean ssde) {
        if (result == 0) {
            return "-";
        } else 
        	return String.valueOf(result);
}
    
    
}