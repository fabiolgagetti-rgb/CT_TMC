package br.unifesp.fisicamedica;
import java.io.File;
import java.util.List;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.Opener;
import ij.measure.Calibration;
import ij.process.ImageProcessor;

public class ImageOpener {
	public static SeriesData activeSeriesData;
	
	 public ImagePlus createImageStack(SeriesData seriesData) {
	        this.activeSeriesData = seriesData;
	        List<File> files = seriesData.getFiles();
	        ImageStack stack = new ImageStack();
	        Calibration stackCalibration = null;
	        boolean allSameCalibration = true;

	        for (File file : files) {
	            try {
	                ImagePlus imp = new Opener().openImage(file.getAbsolutePath());
	                if (imp == null) continue;

	                Calibration cal = imp.getCalibration();
	                if (stackCalibration == null) {
	                    stackCalibration = cal.copy();
	                } else if (!cal.equals(stackCalibration)) {
	                    allSameCalibration = false;
	                }

	                ImageProcessor ip = imp.getProcessor();
	                String metadata = seriesData.getMetadata(file);
	                stack.addSlice(metadata != null ? metadata : "", ip);
	            } catch (Exception e) {
	                IJ.log("Unable to open file: " + file.getAbsolutePath());
	            }
	        }

	        if (stack.getSize() > 0) {
	            ImagePlus result = new ImagePlus(seriesData.getSeriesDescription(), stack);

	            if (allSameCalibration && stackCalibration != null) {
	                result.setCalibration(stackCalibration);
	            }
	            return result;
	        }
	        return null;
	 }
    
   public static SeriesData getActiveSeriesData(){
    	return activeSeriesData;
    }
}

