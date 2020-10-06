package com.riscure.trs;

import com.riscure.trs.enums.Encoding;
import com.riscure.trs.parameter.trace.TraceParameter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.FloatBuffer;
import java.util.Map;

/**
 * Trace contains the data related to one consecutive array of samples,
 * including potential associated data and a title
 */
public class Trace {
    /**
     * Factory method. This will copy the sample array for stability.
     * @param sample the sample array
     * @return a new trace object holding the provided samples
     */
    public static Trace create(float[] sample) {
        return new Trace(sample.clone());
    }

    /**
     * Factory method. This will copy the provided arrays for stability.
     * @deprecated As of TraceSet V2, it is no longer recommended to create traces
     * with raw data. Please use {@link #create(String, float[], Map<String, TraceParameter >)} instead.
     * @param title the trace title
     * @param data the communication data array
     * @param sample the sample array
     * @return a new trace object holding the provided samples
     */
    @Deprecated
    public static Trace create(String title, byte[] data, float[] sample) {
        return new Trace(title, data.clone(), sample.clone());
    }

    /**
     * Factory method. This will copy the provided arrays for stability.
     * @param title the trace title
     * @param sample the sample array
     * @param parameters the parameters to be saved with every trace
     * @return a new trace object holding the provided information
     */
    public static Trace create(String title, float[] sample, Map<String, TraceParameter> parameters) throws IOException {
        return new Trace(title, sample.clone(), parameters);
    }

    /**
     * Factory method. This will copy the provided arrays for stability.
     * @deprecated As of TraceSet V2, it is no longer recommended to create traces
     * with raw data. Please use {@link #create(String, float[], Map)} instead.
     * @param title the trace title
     * @param data the communication data array
     * @param sample the sample array
     * @param sampleFrequency the associated sample frequency
     * @return a new trace object holding the provided samples
     */
    @Deprecated
    public static Trace create(String title, byte[] data, float[] sample, float sampleFrequency) {
        return new Trace(title, data.clone(), sample.clone(), sampleFrequency);
    }

    /**
     * Factory method. This will copy the provided arrays for stability.
     * @param title the trace title
     * @param sample the sample array
     * @param sampleFrequency the associated sample frequency
     * @param parameters the parameters to be saved with every trace
     * @return a new trace object holding the provided information
     */
    public static Trace create(String title, float[] sample, float sampleFrequency, Map<String, TraceParameter> parameters) throws IOException {
        return new Trace(title, sample.clone(), sampleFrequency, parameters);
    }

    /**
     * Creates a new instance of Trace which contains only a sample array
     * Do not modify the sample array, it may be used in the core!
     * 
     * @param sample Sample values. Do not modify
     */
    public Trace(float[] sample) {
        this.sample = FloatBuffer.wrap(sample);
    }

    /**
     * Creates a new instance of Trace containing title, (crypto) data and sample array
     * Do not modify the sample array, it may be used in the core!
     * @deprecated As of TraceSet V2, it is no longer recommended to create traces
     * with raw data. Please use {@link #Trace(String, float[], Map)} instead.
     * 
     * @param title
     *            Local title for this trace
     * @param data
     *            Supplementary (crypto) data
     * @param sample
     *            Sample values. Do not modify
     */
    @Deprecated
    public Trace(String title, byte[] data, float[] sample) {
        this.title = title;
        this.data = data;
        this.sample = FloatBuffer.wrap(sample);
    }

    /**
     * Creates a new instance of Trace containing title, (crypto) data and sample array
     * Do not modify the sample array, it may be used in the core!
     *
     * @param title Local title for this trace
     * @param sample Sample values. Do not modify
     * @param parameters the parameters to be saved with every trace
     */
    public Trace(String title, float[] sample, Map<String, TraceParameter> parameters) throws IOException {
        this.title = title;
        this.sample = FloatBuffer.wrap(sample);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (Map.Entry<String, TraceParameter> entry: parameters.entrySet()) {
            baos.write(entry.getValue().serialize());
        }
        this.data = baos.toByteArray();
        this.traceParameters = parameters;
    }

    /**
     * Creates a new instance of Trace containing title, (crypto) data and sample array
     * Do not modify the sample array, it may be used in the core!
     * @deprecated As of TraceSet V2, it is no longer recommended to create traces
     * with raw data. Please use {@link #Trace(String, float[], float, Map)} instead.
     *
     * @param title
     *            Local title for this trace
     * @param data
     *            Supplementary (crypto) data
     * @param sample
     *            Sample values. Do not modify
     * @param sampleFrequency
     *            Sampling frequency at which the samples were acquired.
     */
    @Deprecated
    public Trace(String title, byte[] data, float[] sample, float sampleFrequency) {
        this(title, data, sample);
        this.sampleFrequency = sampleFrequency;
    }

    /**
     * Creates a new instance of Trace containing title, (crypto) data and sample array
     * Do not modify the sample array, it may be used in the core!
     *
     * @param title
     *            Local title for this trace
     * @param sample
     *            Sample values. Do not modify
     * @param sampleFrequency
     *            Sampling frequency at which the samples were acquired.
     * @param parameters the parameters to be saved with every trace
     */
    public Trace(String title, float[] sample, float sampleFrequency, Map<String, TraceParameter> parameters) throws IOException {
        this(title, sample, parameters);
        this.sampleFrequency = sampleFrequency;
    }

    /**
     * Get the sample array, no shift corrections are done.
     * @return the sample array
     */
    public float[] getSample() {
        return sample.array();
    }

    /**
     * Force float sample coding
     */
    public void forceFloatCoding() {
        isReal = true;
    }

    /**
     * Get the preferred data type to store samples
     * 
     * @return the preferred data type to store samples
     **/
    public int getPreferredCoding() {
        if (!aggregatesValid)
            updateAggregates();

        if (hasIllegalValues)
            return Encoding.ILLEGAL.getValue();
        if (isReal)
            return Encoding.FLOAT.getValue();
        if (max > Short.MAX_VALUE || min < Short.MIN_VALUE)
            return Encoding.INT.getValue();
        if (max > Byte.MAX_VALUE || min < Byte.MIN_VALUE)
            return Encoding.SHORT.getValue();
        return Encoding.BYTE.getValue();
    }

    private void updateAggregates() {
        float[] sample = getSample();

        // Update aggregates
        for (float f : sample) {
            if (f > max)
                max = f;
            if (f < min)
                min = f;
            if (f != (int) f)
                isReal = true;
            if (Float.isNaN(f) || Float.isInfinite(f) || f == Float.POSITIVE_INFINITY)
                hasIllegalValues = true;
        }

        aggregatesValid = true;
    }

    /**
     * Get the trace title
     * 
     * @return The title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Set the title for this trace.
     * 
     * @param title the new title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Get the trace sample frequency
     * 
     * @return The sample frequency
     */
    public float getSampleFrequency() {
        return sampleFrequency;
    }

    /**
     * Set the sample frequency for this trace.
     * 
     * @param sampleFrequency the sample frequency
     */
    public void setSampleFrequency(float sampleFrequency) {
        this.sampleFrequency = sampleFrequency;
    }

    /**
     * Get the supplementary (crypto) data of this trace.
     *
     * @return the supplementary data of this trace
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Set the supplementary (crypto) data for this trace.
     * 
     * @param data the new (crypto) data
     */
    public void setData(byte[] data) {
        this.data = data;
    }

    /**
     * Get the parameter associated with the name, or null if not found
     *
     * @param name the name of the parameter to retrieve
     * @return the parameter associated with the name, or null if not found
     */
    public TraceParameter getTraceParameter(String name) {
        return traceParameters.get(name);
    }

    /**
     * Get the supplementary (crypto) data of this trace as a hexadecimal string.
     *
     * @return the supplementary (crypto) data of this trace as a hexadecimal string
     */
    public String getDataString() {
        return new BigInteger(data).toString(16);
    }

    /**
     * Get the number of samples that this trace is shifted.
     *
     * @return the number of samples that this trace is shifted
     */
    public int getShifted() {
        return shifted;
    }

    /**
     * Set the number of samples that this trace is shifted
     * 
     * @param shifted number of shifted samples
     */
    public void setShifted(int shifted) {
        this.shifted = shifted;
    }

    /**
     * Get the length of the sample array.
     *
     * @return the length of the sample array
     */
    public int getNumberOfSamples() {
        return sample.limit();
    }

    /** A map of all custom named trace parameters */
    private Map<String, TraceParameter> traceParameters;
    /** list of samples */
    private FloatBuffer sample;
    /** trace title */
    public String title = null;
    /** trace (crypto) data */
    public byte[] data = null;
    /** number of samples shifted */
    public int shifted = 0;
    /** trace set including this trace */
    public TraceSet ts = null;
    /** sample frequency of this trace */
    public float sampleFrequency = 1;
    /**
     * Indicates whether the aggregates are valid: hasIllegalValues, isReal,
     * max, min
     */
    private boolean aggregatesValid = false;
    /** whether the trace contains illegal float values */
    private boolean hasIllegalValues = false;
    /** whether the trace contains real values */
    private boolean isReal = false;
    /** maximal value used in trace */
    private float max = 0, min = 0;
}
