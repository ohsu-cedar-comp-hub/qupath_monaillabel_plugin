package qupath.lib.classifiers.pixel;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.color.model.ColorModelFactory;

import java.awt.image.ColorModel;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractOpenCVPixelClassifier implements PixelClassifier {

    private static final Logger logger = LoggerFactory.getLogger(AbstractOpenCVPixelClassifier.class);

    private transient ColorModel colorModelProbabilities;
    private transient ColorModel colorModelClassifications;
    
    private boolean doSoftmax;
    private boolean do8Bit;

    private PixelClassifierMetadata metadata;
    
    AbstractOpenCVPixelClassifier(PixelClassifierMetadata metadata, boolean do8Bit) {
        this(metadata, do8Bit, false);
    }

    AbstractOpenCVPixelClassifier(PixelClassifierMetadata metadata, boolean do8Bit, boolean doSoftmax) {
        this.metadata = metadata;
        this.do8Bit = do8Bit;
        this.doSoftmax = doSoftmax;
    }

    boolean do8Bit() {
    	return this.do8Bit;
    }
    
    boolean doSoftmax() {
    	return doSoftmax;
    }
    
    protected synchronized ColorModel getClassificationsColorModel() {
    	if (colorModelClassifications == null) {
            colorModelClassifications = ColorModelFactory.getIndexedColorModel(metadata.getChannels());
    	}
    	return colorModelClassifications;
    }
    
    
    protected synchronized ColorModel getProbabilityColorModel() {
    	if (colorModelProbabilities == null) {
    		if (do8Bit())
    			colorModelProbabilities = ColorModelFactory.geProbabilityColorModel8Bit(metadata.getChannels());
    		else
    			colorModelProbabilities = ColorModelFactory.geProbabilityColorModel32Bit(metadata.getChannels());
    	}
    	return colorModelProbabilities;
    }
    

    public PixelClassifierMetadata getMetadata() {
        return metadata;
    }


    /**
     * Apply Softmax along channels dimension.
     * @param mat
     */
    void applySoftmax(Mat mat) {
    	// This code doesn't use OpenCV methods because Infinity can sometimes 
    	// occur & needs handled as a special case.
    	var indexer = mat.createIndexer();
    	int rows = (int)indexer.rows();
    	int cols = (int)indexer.cols();
    	int channels = (int)indexer.channels();
    	double[] values = new double[channels];
    	long[] inds = new long[3];
    	for (int r = 0; r < rows; r++) {
    		inds[0] = r;
        	for (int c = 0; c < cols; c++) {
        		inds[1] = c;
        		double sum = 0;
        		for (int k = 0; k < channels; k++) {
            		inds[2] = k;
        			double temp = Math.exp(indexer.getDouble(inds));
        			values[k] = temp;
        			sum += temp;
        		}
        		if (Double.isInfinite(sum)) {
            		for (int k = 0; k < channels; k++) {
                		inds[2] = k;
                		if (Double.isInfinite(values[k]))
                			indexer.putDouble(inds, 1);
                		else
                			indexer.putDouble(inds, 0);
            		}
        		} else {
	        		for (int k = 0; k < channels; k++) {
	            		inds[2] = k;
	            		double temp = values[k] / sum;
	        			indexer.putDouble(inds, temp);
	        		}
        		}
        	}    		
    	}
    }

    /**
     * Create a Scalar from between 1 and 4 double values.
     *
     * @param values
     * @return
     */
    opencv_core.Scalar toScalar(double... values) {
        if (values.length == 1)
            return opencv_core.Scalar.all(values[0]);
        else if (values.length == 2)
            return new opencv_core.Scalar(values[0], values[1]);
        else if (values.length == 3)
            return new opencv_core.Scalar(values[0], values[1], values[2], 0);
        else if (values.length == 4)
            return new opencv_core.Scalar(values[0], values[1], values[2], values[3]);
        throw new IllegalArgumentException("Invalid number of entries - need between 1 & 4 entries to create an OpenCV scalar, not " + values.length);
    }


    public List<String> getChannelNames() {
        return metadata.getChannels().stream().map(c -> c.getName()).collect(Collectors.toList());
    }

    public double getRequestedPixelSizeMicrons() {
        return metadata.getInputPixelSize();
    }

}