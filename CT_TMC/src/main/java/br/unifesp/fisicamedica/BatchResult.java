package br.unifesp.fisicamedica;

import java.util.Map;

public class BatchResult {
    public String patientName;
    public String examDate;
    public String seriesDescription;
    public String studyDescription;
    public String protocol;
    public Map<String, Double> scalarResults;
    public Map<String, double[]> arrayResults;

    public BatchResult(String patientName, String examDate, String studyDescription, String seriesDescription, String protocol,
                       Map<String, Double> scalarResults, Map<String, double[]> arrayResults) {
        this.patientName = patientName;
        this.examDate = examDate;
        this.seriesDescription = seriesDescription;
        this.studyDescription = studyDescription;
        this.protocol = protocol;
        this.scalarResults = scalarResults;
        this.arrayResults = arrayResults;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getExamDate() {
        return examDate;
    }

    public String getSeriesDescription() {
        return seriesDescription;
    }

    public String getProtocol() {
        return protocol;
    }

    public Map<String, Double> getScalarResults() {
        return scalarResults;
    }

    public Map<String, double[]> getArrayResults() {
        return arrayResults;
    }
}