/*
* Copyright (C) 2010-2015 José Luis Risco Martín <jlrisco@ucm.es> and
* José Manuel Colmenar Verdugo <josemanuel.colmenar@urjc.es>
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <http://www.gnu.org/licenses/>.
*
* Contributors:
*  - Josué Pagán Ortíz
*  - José Luis Risco Martín
*/
package test.parkinson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jeco.algorithm.moge.AbstractProblemGE;
import jeco.operator.evaluator.AbstractPopEvaluator;
import jeco.problem.Solution;
import jeco.problem.Variable;

/**
 * Class to manage a normalized data table. Originally, the data table is passed
 * to this class as a regular data table. After the constructor, the data table
 * is normalized in the interval [1,2].
 *
 * @author José Luis Risco Martín
 */
public class DataTable {
    
    private static final Logger logger = Logger.getLogger(DataTable.class.getName());
    
    protected ParkinsonClassifier problem;
    protected String baseTrainingPath = null;
    protected ArrayList<double[]> trainingTable = new ArrayList<>();
    protected int idxBegin = -1;
    protected int idxEnd = -1;
    protected int numInputColumns = 0;
    protected int numTotalColumns = 0;
    protected double[] xLs = null;
    protected double[] xHs = null;
    
    protected double bestFitness = Double.POSITIVE_INFINITY;
    
    protected String foot = null;
    protected Double patientPDLevel = null;
    protected ArrayList<double[]> clinicalTable = new ArrayList<>();
    protected int lengthIni = 0;
    protected int lengthEnd = 0;
    int[][] patientsIdXs = new int[clinicalTable.size()][2];
    
    public DataTable(ParkinsonClassifier problem, String baseTrainingPath, int idxBegin, int idxEnd) throws IOException {
        this.problem = problem;
        this.baseTrainingPath = baseTrainingPath;
        logger.info("Reading data file ...");
        
        readData(problem.properties.getProperty("ClinicalPath"), clinicalTable, false);
        
        fillTrainingDataTable(trainingTable);
        this.idxBegin = (idxBegin == -1) ? 0 : idxBegin;
        this.idxEnd = (idxEnd == -1) ? trainingTable.size() : idxEnd;
        logger.info("Evaluation interval: [" + this.idxBegin + "," + this.idxEnd + ")");
        logger.info("... done.");
    }
    
    public DataTable(ParkinsonClassifier problem, String baseTrainingPath) throws IOException {
        this(problem, baseTrainingPath, -1, -1);
    }
    
    
    public final void fillTrainingDataTable(ArrayList<double[]> dataTable) throws IOException {
// exercises : exercises = {walk, cycling, hoolToe};
        String exercises = problem.properties.getProperty("Exercises");
        String[] exercisesTrunc = exercises.split(",");
        
        numInputColumns = 0;
        numTotalColumns = 0;
        patientsIdXs = new int[clinicalTable.size()][2];
        
        for (int p = 0; p < clinicalTable.size(); p++) {
            String patientID = String.valueOf((int)clinicalTable.get(p)[1]);               // Get the code GAxxxxxx
            patientPDLevel = clinicalTable.get(p)[8];              // Get the level scale H&Y
            logger.info("PatientID: GA" + patientID + ", PDlevel: " + patientPDLevel);
            
            String absoluteBasePath = problem.properties.getProperty("DataPathBase");
            
            lengthIni = trainingTable.size();
            for (int f = 0; f<=1; f++){	// For each foot
                foot = (f == 0) ? "RightFoot_" : "LeftFoot_";
                
                for (String ex : exercisesTrunc) {
                    String absoluteDataPath = absoluteBasePath + "/GA" + patientID + "/" + foot + ex + ".csv";
                    logger.info("Data: " + absoluteDataPath);
                    readData(absoluteDataPath, trainingTable, true);
                }
            }
            patientsIdXs[p][0] = lengthIni;
            patientsIdXs[p][1] = trainingTable.size()-1;
        }
    }
    
    
    public final void readData(String dataPath, ArrayList<double[]> dataTable, Boolean addOutputLine) throws IOException {
        File file = new File(dataPath);
        if (file.exists()){
            
            try (BufferedReader reader = new BufferedReader(new FileReader(new File(dataPath)))) {
                String line;
                
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] parts = line.split(";");
                    if (parts.length == 1){
                        parts = line.split(",");
                    }
                    if (parts.length > numInputColumns) {
                        numInputColumns = parts.length;
                        numTotalColumns = numInputColumns + 1;
                    }
                    
                    double[] dataLine = new double[numTotalColumns];
                    for (int j = 0; j < numInputColumns; j++) {
                        dataLine[j] = Double.valueOf(parts[j]);
                    }
                    if (addOutputLine) {
                        dataLine[numTotalColumns-1] = patientPDLevel;
                    }
                    dataTable.add(dataLine);
                }
                reader.close();
            }
        }
        else {
            logger.info("File: " + dataPath + "DOES NOT EXIST");
        }
    }
    
    
    public double evaluate(AbstractPopEvaluator evaluator, Solution<Variable<Integer>> solution, int patientNo, int idx) {
        String functionAsString = problem.generatePhenotype(solution).toString();
        double fitness = computeFitness(evaluator, patientNo, idx);
        if (fitness < bestFitness) {
            bestFitness = fitness;
            for (int i = 0; i < numTotalColumns; ++i) {
                if (i == 0) {
                    functionAsString = functionAsString.replaceAll("getVariable\\(" + i + ",", "yr\\(");
                } else if (i == numTotalColumns - 1) {
                    functionAsString = functionAsString.replaceAll("getVariable\\(" + i + ",", "yp\\(");
                } else {
                    functionAsString = functionAsString.replaceAll("getVariable\\(" + i + ",", "u" + i + "\\(");
                }
            }
            logger.info("Best FIT=" + (100 * (1 - bestFitness)) + "; Expresion=" + functionAsString);
        }
        return fitness;
    }
    
    public double computeFitness(AbstractPopEvaluator evaluator, int patientNo, int idx) {
        Double resultGE =  evaluator.evaluate(idx, -1);
        
        Double qResult = quantizer(resultGE);
        
        // Get the PD H&Y level and compute fitness
        Double fitness = Math.abs(qResult-clinicalTable.get(patientNo)[8]);
        return fitness;
    }
    
    public ArrayList<double[]> getDataTable(String  type) {
        switch (type) {
            case "training":
                return trainingTable;
            case "clinical":
                return clinicalTable;
            default:
                return trainingTable;
        }
    }
    
    public int[][] getPatientsIdXs(){
        return patientsIdXs;
    }
    
    public Double quantizer(Double currFitness) {
        // Hardcode H&Y Parkinson Scale 0 to 3 (0 means no PD)
        Double qFitness = 0.0;
        
        if (currFitness >= 2.5) {
            qFitness = 3.0;
        } else if (currFitness >= 1.5){
            qFitness = 2.0;
        } else if (currFitness >= 0.5){
            qFitness = 2.0;
        } else {
            qFitness = 0.0;
        }
        return qFitness;
    }
    
}