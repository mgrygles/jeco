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
 *  - José Luis Risco Martín
 *  - José Manuel Colmenar Verdugo
 */
package jeco.algorithm.ge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.Observer;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import jeco.algorithm.ga.SimpleGeneticAlgorithm;
import jeco.algorithm.moge.AbstractProblemGE;
import jeco.algorithm.moge.Phenotype;
import jeco.operator.comparator.SimpleDominance;
import jeco.operator.crossover.SinglePointCrossover;
import jeco.operator.evaluator.AbstractPopEvaluator;
import jeco.operator.mutation.IntegerFlipMutation;
import jeco.operator.selection.BinaryTournament;
import jeco.problem.Solution;
import jeco.problem.Solutions;
import jeco.problem.Variable;
import jeco.util.NormalizedDataTable;
import jeco.util.compiler.MyCompiler;
import jeco.util.compiler.MyLoader;
import jeco.util.logger.JecoLogger;

public class GramEvalTemporalModel extends AbstractProblemGE {

    private static final Logger logger = Logger.getLogger(GramEvalTemporalModel.class.getName());

    protected int threadId;
    protected MyCompiler compiler;
    protected NormalizedDataTable dataTable;
    protected Properties properties;
    protected AbstractPopEvaluator evaluator;

    public GramEvalTemporalModel(Properties properties, int threadId) throws IOException {
        super(properties.getProperty("BnfPathFile"), 1);
        this.properties = properties;
        this.threadId = threadId;
        compiler = new MyCompiler(properties);
        dataTable = new NormalizedDataTable(this, properties.getProperty("TrainingPath"), Boolean.valueOf(properties.getProperty("NormalizeTable", "true")), Integer.valueOf(properties.getProperty("IdxBegin", "-1")), Integer.valueOf(properties.getProperty("IdxEnd", "-1")));
    }

    @Override
    public void evaluate(Solutions<Variable<Integer>> solutions) {
        StringBuilder currentJavaFile = new StringBuilder();

        currentJavaFile.append("public class PopEvaluator").append(threadId).append(" extends jeco.core.operator.evaluator.AbstractPopEvaluator {\n\n");

        currentJavaFile.append("\tpublic double MyDrv(int from, int to, int idxVar) {\n");
        currentJavaFile.append("\t\treturn (getVariable(idxVar, to) - getVariable(idxVar, from))/(to - from + 1);\n");
        currentJavaFile.append("\t}\n\n");

        currentJavaFile.append("\tpublic double MySum(int from, int to, int idxVar) {\n");
        currentJavaFile.append("\t\tdouble res = 0.0;\n");
        currentJavaFile.append("\t\tfor (int i = from; i <= to; ++i) {\n");
        currentJavaFile.append("\t\t\tres += getVariable(idxVar, i);\n");
        currentJavaFile.append("\t\t}\n");
        currentJavaFile.append("\t\treturn res;\n");
        currentJavaFile.append("\t}\n\n");

        currentJavaFile.append("\tpublic double MyAvg(int from, int to, int idxVar) {\n");
        currentJavaFile.append("\t\tdouble res = 0.0;\n");
        currentJavaFile.append("\t\tint size = to - from + 1;\n");
        currentJavaFile.append("\t\tres = MySum(from, to, idxVar) / size;\n");
        currentJavaFile.append("\t\treturn res;\n");
        currentJavaFile.append("\t}\n\n");

        currentJavaFile.append("\tpublic void evaluateExpression(int idxExpr) {\n");
        currentJavaFile.append("\t\tdouble[] rowPred = dataTable.get(0);\n");
        currentJavaFile.append("\t\trowPred[rowPred.length - 1] = rowPred[0];\n");
        currentJavaFile.append("\t\tfor (int k = 0; k < dataTable.size() - 1; ++k) {\n");
        currentJavaFile.append("\t\t\trowPred = dataTable.get(k + 1);\n");
        currentJavaFile.append("\t\t\trowPred[rowPred.length - 1] = evaluate(idxExpr, k);\n");
        currentJavaFile.append("\t\t}\n");
        currentJavaFile.append("\t}\n\n");

        currentJavaFile.append("\tpublic double getVariable(int idxVar, int k) {\n");
        currentJavaFile.append("\t\tif (k < 0) {\n");
        currentJavaFile.append("\t\t\treturn dataTable.get(0)[idxVar];\n");
        currentJavaFile.append("\t\t} else {\n");
        currentJavaFile.append("\t\t\treturn dataTable.get(k)[idxVar];\n");
        currentJavaFile.append("\t\t}\n");
        currentJavaFile.append("\t}\n\n");

        currentJavaFile.append("\tpublic double evaluate(int idxExpr, int k) {\n");
        currentJavaFile.append("\t\tdouble result = 0.0;\n");
        currentJavaFile.append("\t\ttry {\n");

        currentJavaFile.append("\t\t\tswitch(idxExpr) {\n");
        for (int i = 0; i < solutions.size(); ++i) {
            currentJavaFile.append("\t\t\t\tcase ").append(i).append(":\n");
            Solution<Variable<Integer>> solution = solutions.get(i);
            Phenotype phenotype = generatePhenotype(solution);
            if (correctSol) {
                currentJavaFile.append("\t\t\t\t\tresult = ").append(phenotype.toString()).append(";\n");
            } else {
                currentJavaFile.append("\t\t\t\t\tresult = Double.POSITIVE_INFINITY;\n");
            }
            currentJavaFile.append("\t\t\t\t\tbreak;\n");
        }
        currentJavaFile.append("\t\t\t\tdefault:\n");
        currentJavaFile.append("\t\t\t\t\tresult = Double.POSITIVE_INFINITY;\n");
        currentJavaFile.append("\t\t\t}\n"); // End switch

        currentJavaFile.append("\t\t}\n"); // End try
        currentJavaFile.append("\t\tcatch (Exception ee) {\n");
        currentJavaFile.append("\t\t\t// System.err.println(ee.getLocalizedMessage());\n");
        currentJavaFile.append("\t\t\tresult = Double.POSITIVE_INFINITY;\n");
        currentJavaFile.append("\t\t}\n"); // End catch
        currentJavaFile.append("\t\tif(Double.isNaN(result)) {\n");
        currentJavaFile.append("\t\t\tresult = Double.POSITIVE_INFINITY;\n");
        currentJavaFile.append("\t\t}\n");
        currentJavaFile.append("\t\treturn result;\n");
        currentJavaFile.append("\t}\n");
        currentJavaFile.append("}\n");
        // Compilation process:
        try {
            File file = new File(compiler.getWorkDir() + File.separator + "PopEvaluator" + threadId + ".java");
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(currentJavaFile.toString());
            writer.flush();
            writer.close();
            LinkedList<String> filePaths = new LinkedList<>();
            filePaths.add(file.getAbsolutePath());
            boolean sucess = compiler.compile(filePaths);
            if (!sucess) {
                logger.severe("Unable to compile, with errors:");
                logger.severe(compiler.getOutput());
            }
        } catch (Exception ex) {
            logger.severe(ex.getLocalizedMessage());
        }
        // And now we evaluate all the solutions with the compiled file:
        evaluator = null;
        try {
            evaluator = (AbstractPopEvaluator) (new MyLoader(compiler.getWorkDir())).loadClass("PopEvaluator" + threadId).newInstance();
            evaluator.setDataTable(dataTable.getTrainingTable());
        } catch (Exception ex) {
            logger.severe(ex.getLocalizedMessage());
        }
        for (int i = 0; i < solutions.size(); ++i) {
            Solution<Variable<Integer>> solution = solutions.get(i);
            double fitness = dataTable.evaluate(evaluator, solution, i);
            if (Double.isNaN(fitness)) {
                logger.info("I have a NaN number here");
            }
            solution.getObjectives().set(0, fitness);
        }
    }

    @Override
    public void evaluate(Solution<Variable<Integer>> solution) {
        logger.severe("The solutions should be already evaluated. You should not see this message.");
    }

    @Override
    public void evaluate(Solution<Variable<Integer>> solution, Phenotype phenotype) {
        logger.severe("The solutions should be already evaluated. You should not see this message.");
    }

    @Override
    public GramEvalTemporalModel clone() {
        GramEvalTemporalModel clone = null;
        try {
            clone = new GramEvalTemporalModel(properties, threadId + 1);
        } catch (IOException ex) {
            logger.severe(ex.getLocalizedMessage());
        }
        return clone;
    }

    public static Properties loadProperties(String propertiesFilePath) {
        Properties properties = new Properties();
        try {
            properties.load(new BufferedReader(new FileReader(new File(propertiesFilePath))));
            File clsDir = new File(properties.getProperty("WorkDir"));
            URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Class<URLClassLoader> sysclass = URLClassLoader.class;
            Method method = sysclass.getDeclaredMethod("addURL", new Class[]{URL.class});
            method.setAccessible(true);
            method.invoke(sysloader, new Object[]{clsDir.toURI().toURL()});
        } catch (Exception ex) {
            logger.severe(ex.getLocalizedMessage());
        }
        return properties;
    }

    public static void runGE(Properties properties, int threadId, Observer obs) {
        JecoLogger.setup(properties.getProperty("LoggerBasePath") + "_" + threadId + ".log", Level.parse(properties.getProperty("LoggerLevel")));

        GramEvalTemporalModel problem = null;
        try {
            problem = new GramEvalTemporalModel(properties, threadId);
        } catch (IOException ex) {
            logger.severe(ex.getLocalizedMessage());
        }
        // Second create the algorithm
        IntegerFlipMutation<Variable<Integer>> mutationOperator = new IntegerFlipMutation<>(problem, 1.0 / problem.reader.getRules().size());
        SinglePointCrossover<Variable<Integer>> crossoverOperator = new SinglePointCrossover<>(problem, SinglePointCrossover.DEFAULT_FIXED_CROSSOVER_POINT, SinglePointCrossover.DEFAULT_PROBABILITY, SinglePointCrossover.AVOID_REPETITION_IN_FRONT);
        SimpleDominance<Variable<Integer>> comparator = new SimpleDominance<>();
        BinaryTournament<Variable<Integer>> selectionOp = new BinaryTournament<>(comparator);
        SimpleGeneticAlgorithm<Variable<Integer>> algorithm = new SimpleGeneticAlgorithm<>(problem, Integer.valueOf(properties.getProperty("NumIndividuals")), Integer.valueOf(properties.getProperty("NumGenerations")), true, mutationOperator, crossoverOperator, selectionOp);
        if (obs != null) {
            algorithm.addObserver(obs);
        }
        algorithm.initialize();
        algorithm.execute();
    }

    private static DefaultTableModel solutionsTableModel = new DefaultTableModel();

    public static void setViewTable(JTable tableSolutions) {
        solutionsTableModel.setColumnIdentifiers(new String[]{"Obj.", "Solution"});
        tableSolutions.setModel(solutionsTableModel);
    }

    public static void main(String[] args) {
        String propertiesFilePath = "TemporalModel.properties";
        int threadId = 1;
        if (args.length == 1) {
            propertiesFilePath = args[0];
        } else if (args.length >= 2) {
            propertiesFilePath = args[0];
            threadId = Integer.valueOf(args[1]);
        }
        Properties properties = loadProperties(propertiesFilePath);
        runGE(properties, threadId, null);
    }
}
