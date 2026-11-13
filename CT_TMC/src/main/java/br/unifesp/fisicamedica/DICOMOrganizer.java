package br.unifesp.fisicamedica;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

public class DICOMOrganizer {

    public static void organizeDICOMFiles(JFrame parentFrame) {
        // Selecionar diretório de destino
        JFileChooser destinationChooser = new JFileChooser();
        destinationChooser.setDialogTitle("Selecione o diretório de destino para organização");
        destinationChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        int destinationChoice = destinationChooser.showSaveDialog(parentFrame);
        if (destinationChoice != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File destinationDir = destinationChooser.getSelectedFile();
        
        // Verificar se há dados para organizar
        if (DICOMProcessor.patientData.isEmpty()) {
            JOptionPane.showMessageDialog(parentFrame, 
                "Nenhum dado DICOM carregado para organizar.", 
                "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Criar thread para organização (para não travar a interface)
        new Thread(() -> {
            try {
                organizeFiles(destinationDir);
                JOptionPane.showMessageDialog(parentFrame, 
                    "Arquivos organizados com sucesso em: " + destinationDir.getAbsolutePath(), 
                    "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(parentFrame, 
                    "Erro ao organizar arquivos: " + e.getMessage(), 
                    "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    private static void organizeFiles(File destinationDir) throws IOException {
        for (Map.Entry<String, Map<String, Map<String, Map<String, SeriesData>>>> patientEntry : 
             DICOMProcessor.patientData.entrySet()) {
            
            String patientName = sanitizeFileName(patientEntry.getKey());
            File patientDir = new File(destinationDir, patientName);
            
            for (Map.Entry<String, Map<String, Map<String, SeriesData>>> studyEntry : 
                 patientEntry.getValue().entrySet()) {
                
                String studyDescription = sanitizeFileName(studyEntry.getKey());
                File studyDir = new File(patientDir, studyDescription);
                
                for (Map.Entry<String, Map<String, SeriesData>> protocolEntry : 
                     studyEntry.getValue().entrySet()) {
                    
                    String protocolName = sanitizeFileName(protocolEntry.getKey());
                    File protocolDir = new File(studyDir, protocolName);
                    
                    for (Map.Entry<String, SeriesData> seriesEntry : 
                         protocolEntry.getValue().entrySet()) {
                        
                        SeriesData seriesData = seriesEntry.getValue();
                        String seriesDescription = sanitizeFileName(seriesData.getSeriesDescription());
                        File seriesDir = new File(protocolDir, seriesDescription);
                        
                        // Criar diretórios se não existirem
                        if (!seriesDir.exists()) {
                            seriesDir.mkdirs();
                        }
                        
                        // Copiar arquivos para o diretório da série
                        for (File dicomFile : seriesData.getFiles()) {
                            Path source = dicomFile.toPath();
                            Path target = new File(seriesDir, dicomFile.getName()).toPath();
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        }
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Unknown";
        }
        // Remover caracteres inválidos para nomes de arquivos
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}