package de.lmu.ifi.dbs.elki.algorithm.clustering.correlation;

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.DependencyDerivator;
import de.lmu.ifi.dbs.elki.algorithm.clustering.correlation.cash.CASHInterval;
import de.lmu.ifi.dbs.elki.algorithm.clustering.correlation.cash.CASHIntervalSplit;
import de.lmu.ifi.dbs.elki.algorithm.result.CorrelationAnalysisSolution;
import de.lmu.ifi.dbs.elki.algorithm.result.Result;
import de.lmu.ifi.dbs.elki.algorithm.result.clustering.CASHResult;
import de.lmu.ifi.dbs.elki.algorithm.result.clustering.SubspaceClusterMap;
import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.data.ParameterizationFunction;
import de.lmu.ifi.dbs.elki.data.RealVector;
import de.lmu.ifi.dbs.elki.database.Associations;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ObjectAndAssociations;
import de.lmu.ifi.dbs.elki.database.SequentialDatabase;
import de.lmu.ifi.dbs.elki.distance.DoubleDistance;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.WeightedDistanceFunction;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Matrix;
import de.lmu.ifi.dbs.elki.normalization.NonNumericFeaturesException;
import de.lmu.ifi.dbs.elki.utilities.Description;
import de.lmu.ifi.dbs.elki.utilities.HyperBoundingBox;
import de.lmu.ifi.dbs.elki.utilities.Progress;
import de.lmu.ifi.dbs.elki.utilities.UnableToComplyException;
import de.lmu.ifi.dbs.elki.utilities.Util;
import de.lmu.ifi.dbs.elki.utilities.heap.DefaultHeap;
import de.lmu.ifi.dbs.elki.utilities.heap.DefaultHeapNode;
import de.lmu.ifi.dbs.elki.utilities.heap.HeapNode;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.Flag;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.ParameterException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterConstraint;
import de.lmu.ifi.dbs.elki.varianceanalysis.FirstNEigenPairFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

/**
 * Provides the CASH algorithm, an subspace clustering algorithm based on the hough transform.
 * <p>Reference:
 * E. Achtert, C. Boehm, J. David, P. Kroeger, A. Zimek:
 * Robust clustering in arbitraily oriented subspaces.
 * <br>In Proc. 8th SIAM Int. Conf. on Data Mining (SDM'08), Atlanta, GA, 2008
 * </p>
 *
 * @author Elke Achtert
 */
//todo elke hierarchy (later)
public class CASH extends AbstractAlgorithm<ParameterizationFunction> {

    /**
     * OptionID for {@link #MINPTS_PARAM}
     */
    public static final OptionID MINPTS_ID = OptionID.getOrCreateOptionID(
        "cash.minpts",
        "Threshold for minimum number of points in a cluster."
    );

    /**
     * Parameter to specify the threshold for minimum number of points in a cluster,
     * must be an integer greater than 0.
     * <p>Key: {@code -cash.minpts} </p>
     */
    private final IntParameter MINPTS_PARAM = new IntParameter(MINPTS_ID,
        new GreaterConstraint(0));

    /**
     * Holds the value of {@link #MINPTS_PARAM}.
     */
    private int minPts;

    /**
     * OptionID for {@link #MAXLEVEL_PARAM}.
     */
    public static final OptionID MAXLEVEL_ID = OptionID.getOrCreateOptionID(
        "cash.maxlevel",
        "The maximum level for splitting the hypercube."
    );

    /**
     * Parameter to specify the maximum level for splitting the hypercube,
     * must be an integer greater than 0.
     * <p>Key: {@code -cash.maxlevel} </p>
     */
    private final IntParameter MAXLEVEL_PARAM = new IntParameter(MAXLEVEL_ID,
        new GreaterConstraint(0));

    /**
     * Holds the value of {@link #MAXLEVEL_PARAM}.
     */
    private int maxLevel;

    /**
     * OptionID for {@link #MINDIM_PARAM}
     */
    public static final OptionID MINDIM_ID = OptionID.getOrCreateOptionID(
        "cash.mindim",
        "The minimum dimensionality of the subspaces to be found."
    );

    /**
     * Parameter to specify the minimum dimensionality of the subspaces to be found,
     * must be an integer greater than 0.
     * <p>Default value: {@code 1} </p>
     * <p>Key: {@code -cash.mindim} </p>
     */
    private final IntParameter MINDIM_PARAM = new IntParameter(MINDIM_ID,
        new GreaterConstraint(0), 1);

    /**
     * Holds the value of {@link #MINDIM_PARAM}.
     */
    private int minDim;

    /**
     * OptionID for {@link #JITTER_PARAM}
     */
    public static final OptionID JITTER_ID = OptionID.getOrCreateOptionID(
        "cash.jitter",
        "The maximum jitter for distance values."
    );


    /**
     * Parameter to specify the maximum jitter for distance values,
     * must be a double greater than 0.
     * <p>Key: {@code -cash.jitter} </p>
     */
    private final DoubleParameter JITTER_PARAM = new DoubleParameter(JITTER_ID,
        new GreaterConstraint(0));

    /**
     * Holds the value of {@link #JITTER_PARAM}.
     */
    private double jitter;

    /**
     * OptionID for {@link #ADJUST_FLAG}
     */
    public static final OptionID ADJUST_ID = OptionID.getOrCreateOptionID(
        "cash.adjust",
        "Flag to indicate that an adjustment of the applied heuristic for choosing an interval " +
            "is performed after an interval is selected.");

    /**
     * Flag to indicate that an adjustment of the applied heuristic for choosing an interval
     * is performed after an interval is selected.
     * <p>Key: {@code -cash.adjust} </p>
     */
    private final Flag ADJUST_FLAG = new Flag(ADJUST_ID);

    /**
     * Holds the value of {@link #ADJUST_FLAG}.
     */
    private boolean adjust;

    /**
     * The result.
     */
    private CASHResult result;

    /**
     * Holds the dimensionality for noise.
     */
    private int noiseDim;

    /**
     * Holds a set of processed ids.
     */
    private Set<Integer> processedIDs;

    /**
     * The database holding the objects.
     */
    private Database<ParameterizationFunction> database;

    /**
     * Provides a new CASH algorithm,
     * adding parameters
     * {@link #MINPTS_PARAM}, {@link #MAXLEVEL_PARAM}, {@link #MINDIM_PARAM}, {@link #JITTER_PARAM},
     * and flag {@link #ADJUST_FLAG}
     * to the option handler additionally to parameters of super class.
     */
    public CASH() {
        super();

        //parameter minpts
        addOption(MINPTS_PARAM);

        //parameter maxLevel
        addOption(MAXLEVEL_PARAM);

        //parameter minDim
        addOption(MINDIM_PARAM);

        //parameter jitter
        addOption(JITTER_PARAM);

        //flag adjust
        addOption(ADJUST_FLAG);
    }

    /**
     * Performs the CASH algorithm on the given database.
     *
     * @see de.lmu.ifi.dbs.elki.algorithm.Algorithm#run(de.lmu.ifi.dbs.elki.database.Database)
     */
    protected void runInTime(Database<ParameterizationFunction> database) throws IllegalStateException {
        this.database = database;
        if (isVerbose()) {
            StringBuffer msg = new StringBuffer();
            msg.append("\nDB size: ").append(database.size());
            msg.append("\nmin Dim: ").append(minDim);
            verbose(msg.toString());
        }

        try {
            processedIDs = new HashSet<Integer>(database.size());
            noiseDim = database.get(database.iterator().next()).getDimensionality();

            Progress progress = new Progress("Clustering", database.size());
            if (isVerbose()) {
                progress.setProcessed(0);
                progress(progress);
            }

            SubspaceClusterMap clusters = doRun(database, progress);
            if (isVerbose()) {
                StringBuffer msg = new StringBuffer();
                msg.append("\n\nclusters: ").append(clusters.subspaceDimensionalities());
                for (Integer id : clusters.subspaceDimensionalities()) {
                    msg.append("\n         subspaceDim = ").append(id).append(": ").append(clusters.getCluster(id).size()).append(" cluster(s)");
                    msg.append(" [");
                    for (Set<Integer> c : clusters.getCluster(id)) {
                        msg.append(c.size()).append(" ");
                    }
                    msg.append("objects]");
                }
                verbose(msg.toString());
            }

            result = new CASHResult(database, clusters, noiseDim);
        }
        catch (UnableToComplyException e) {
            throw new IllegalStateException(e);
        }
        catch (ParameterException e) {
            throw new IllegalStateException(e);
        }
        catch (NonNumericFeaturesException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns the result of the algorithm.
     *
     * @return the result of the algorithm
     */
    public Result<ParameterizationFunction> getResult() {
        return result;
    }

    /**
     * Returns a description of the algorithm.
     *
     * @return a description of the algorithm
     */
    public Description getDescription() {
        return new Description("CASH",
            "Robust clustering in arbitrarily oriented subspaces",
            "Subspace clustering algorithm based on the hough transform.",
            "E. Achtert, C. Boehm, J. David, P. Kroeger, A. Zimek: " +
                "Robust clustering in arbitraily oriented subspaces. " +
                "In Proc. 8th SIAM Int. Conf. on Data Mining (SDM'08), Atlanta, GA, 2008");
    }

    /**
     * Calls {@link de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm#setParameters(String[]) AbstractAlgorithm#setParameters(args)}
     * and sets additionally the values of the parameters
     * {@link #MINPTS_PARAM}, {@link #MAXLEVEL_PARAM}, {@link #MINDIM_PARAM}, {@link #JITTER_PARAM},
     * and the flag {@link #ADJUST_FLAG}.
     *
     * @see de.lmu.ifi.dbs.elki.utilities.optionhandling.Parameterizable#setParameters(String[])
     */
    public String[] setParameters(String[] args) throws ParameterException {
        String[] remainingParameters = super.setParameters(args);
        // minpts
        minPts = getParameterValue(MINPTS_PARAM);

        // maxlevel
        maxLevel = getParameterValue(MAXLEVEL_PARAM);

        // mindim
        minDim = getParameterValue(MINDIM_PARAM);

        // jitter
        jitter = getParameterValue(JITTER_PARAM);

        // adjust
        adjust = isSet(ADJUST_FLAG);

        return remainingParameters;
    }

    /**
     * Runs the CASH algorithm on the specified database, this method is recursively called
     * until only noise is left.
     *
     * @param database the current database to run the CASH algorithm on
     * @param progress the progress object for verbose messages
     * @return a mapping of subspace dimensionalites to clusters
     * @throws UnableToComplyException     if an error according to the database occurs
     * @throws ParameterException          if the parameter setting is wrong
     * @throws NonNumericFeaturesException if non numeric feature vectors are used
     */
    private SubspaceClusterMap doRun(Database<ParameterizationFunction> database,
                                     Progress progress) throws UnableToComplyException, ParameterException, NonNumericFeaturesException {


        int dim = database.get(database.iterator().next()).getDimensionality();
        SubspaceClusterMap clusterMap = new SubspaceClusterMap(dim);

        // init heap
        DefaultHeap<Integer, CASHInterval> heap = new DefaultHeap<Integer, CASHInterval>(false);
        Set<Integer> noiseIDs = getDatabaseIDs(database);
        initHeap(heap, database, dim, noiseIDs);

        if (debug) {
            StringBuffer msg = new StringBuffer();
            msg.append("\nXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
            msg.append("\nXXXX dim ").append(dim);
            msg.append("\nXXXX database.size ").append(database.size());
            msg.append("\nXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
            debugFine(msg.toString());
        }
        else if (isVerbose()) {
            StringBuffer msg = new StringBuffer();
            msg.append("\nXXXX dim ").append(dim).append(" database.size ").append(database.size());
            verbose(msg.toString());
        }

        // get the ''best'' d-dimensional intervals at max level
        while (!heap.isEmpty()) {
            CASHInterval interval = determineNextIntervalAtMaxLevel(heap);
            if (debug) {
                debugFine("\nnext interval in dim " + dim + ": " + interval);
            }
            else if (isVerbose()) {
                verbose("\nnext interval in dim " + dim + ": " + interval);
            }

            // only noise left
            if (interval == null) {
                break;
            }

            // do a dim-1 dimensional run
            Set<Integer> clusterIDs = new HashSet<Integer>();
            if (dim > minDim + 1) {
                Set<Integer> ids;
                Matrix basis_dim_minus_1;
                if (adjust) {
                    ids = new HashSet<Integer>();
                    basis_dim_minus_1 = runDerivator(database, dim, interval, ids);
                }
                else {
                    ids = interval.getIDs();
                    basis_dim_minus_1 = determineBasis(interval.centroid());
                }

                Database<ParameterizationFunction> db = buildDB(dim, basis_dim_minus_1, ids, database);
                if (db.size() != 0) {
                    SubspaceClusterMap clusterMap_dim_minus_1 = doRun(db, progress);

                    // add result of dim-1 to this result
                    for (Integer d : clusterMap_dim_minus_1.subspaceDimensionalities()) {
                        List<Set<Integer>> clusters_d = clusterMap_dim_minus_1.getCluster(d);
                        for (Set<Integer> clusters : clusters_d) {
                            clusterMap.add(d, clusters, this.database);
                            noiseIDs.removeAll(clusters);
                            clusterIDs.addAll(clusters);
                            processedIDs.addAll(clusters);
                        }
                    }
                }
            }
            // dim == minDim
            else {
                clusterMap.add(dim - 1, interval.getIDs(), this.database);
                noiseIDs.removeAll(interval.getIDs());
                clusterIDs.addAll(interval.getIDs());
                processedIDs.addAll(interval.getIDs());
            }

            // reorganize heap
            Vector<HeapNode<Integer, CASHInterval>> heapVector = heap.copy();
            heap.clear();
            for (HeapNode<Integer, CASHInterval> heapNode : heapVector) {
                CASHInterval currentInterval = heapNode.getValue();
                currentInterval.removeIDs(clusterIDs);
                if (currentInterval.getIDs().size() >= minPts) {
                    heap.addNode(new DefaultHeapNode<Integer, CASHInterval>(currentInterval.priority(), currentInterval));
                }
            }

            if (isVerbose()) {
                progress.setProcessed(processedIDs.size());
                progress(progress);
            }
        }

        // put noise to clusters
        if (!noiseIDs.isEmpty()) {
            if (dim == noiseDim) {
                clusterMap.addToNoise(noiseIDs);
                processedIDs.addAll(noiseIDs);
            }
            else if (noiseIDs.size() >= minPts) {
                clusterMap.add(dim, noiseIDs, this.database);
                processedIDs.addAll(noiseIDs);
            }
        }

        if (debug) {
            StringBuffer msg = new StringBuffer();
            msg.append("\nnoise fuer dim ").append(dim).append(": ").append(noiseIDs.size());
            msg.append("\nclusters fuer dim= ").append(dim).append(": ").append(clusterMap.subspaceDimensionalities());
            for (Integer id : clusterMap.subspaceDimensionalities()) {
                msg.append("         corrDim = ");
                msg.append(id);
                msg.append(": ");
                msg.append(clusterMap.getCluster(id).size());
                msg.append(" cluster(s)");
                msg.append(" [");
                for (Set<Integer> c : clusterMap.getCluster(id)) {
                    msg.append(c.size()).append(" ");
                }
                msg.append("\nobjects]");
            }
            debugFine(msg.toString());
        }

        if (isVerbose()) {
            progress.setProcessed(processedIDs.size());
            progress(progress);
        }


        return clusterMap;
    }

    /**
     * Initializes the heap with the root intervals.
     *
     * @param heap     the heap to be initialized
     * @param database the database storing the paramterization functions
     * @param dim      the dimensionality of the database
     * @param ids      the ids of the database
     */
    private void initHeap(DefaultHeap<Integer, CASHInterval> heap, Database<ParameterizationFunction> database, int dim, Set<Integer> ids) {
        CASHIntervalSplit split = new CASHIntervalSplit(database, minPts);

        // determine minimum and maximum function value of all functions
        double[] minMax = determineMinMaxDistance(database, dim);


        double d_min = minMax[0];
        double d_max = minMax[1];
        double dIntervalLength = d_max - d_min;
        int numDIntervals = (int) Math.ceil(dIntervalLength / jitter);
        double dIntervalSize = dIntervalLength / numDIntervals;
        double[] d_mins = new double[numDIntervals];
        double[] d_maxs = new double[numDIntervals];

        if (debug) {
            StringBuffer msg = new StringBuffer();
            msg.append("\nd_min ").append(d_min);
            msg.append("\nd_max ").append(d_max);
            msg.append("\nnumDIntervals ").append(numDIntervals);
            msg.append("\ndIntervalSize ").append(dIntervalSize);
            debugFine(msg.toString());
        }
        else if (isVerbose()) {
            StringBuffer msg = new StringBuffer();
            msg.append("\nd_min ").append(d_min);
            msg.append("\nd_max ").append(d_max);
            msg.append("\nnumDIntervals ").append(numDIntervals);
            msg.append("\ndIntervalSize ").append(dIntervalSize);
            verbose(msg.toString());
        }

        // alpha intervals
        double[] alphaMin = new double[dim - 1];
        double[] alphaMax = new double[dim - 1];
        Arrays.fill(alphaMax, Math.PI);

        for (int i = 0; i < numDIntervals; i++) {
            if (i == 0) {
                d_mins[i] = d_min;
            }
            else {
                d_mins[i] = d_maxs[i - 1];
            }

            if (i < numDIntervals - 1) {
                d_maxs[i] = d_mins[i] + dIntervalSize;
            }
            else {
                d_maxs[i] = d_max - d_mins[i];
            }

            HyperBoundingBox alphaInterval = new HyperBoundingBox(alphaMin, alphaMax);
            Set<Integer> intervalIDs = split.determineIDs(ids, alphaInterval, d_mins[i], d_maxs[i]);
            if (intervalIDs != null && intervalIDs.size() >= minPts) {
                CASHInterval rootInterval = new CASHInterval(alphaMin, alphaMax, split, intervalIDs, 0, 0, d_mins[i], d_maxs[i]);
                heap.addNode(new DefaultHeapNode<Integer, CASHInterval>(rootInterval.priority(), rootInterval));
            }
        }

        if (debug) {
            StringBuffer msg = new StringBuffer();
            msg.append("\nheap.size ").append(heap.size());
            debugFiner(msg.toString());
        }
    }

    /**
     * Builds a dim-1 dimensional database where the objects are projected into the specified subspace.
     *
     * @param dim      the dimensionality of the database
     * @param basis    the basis defining the subspace
     * @param ids      the ids for the new database
     * @param database the database storing the paramterization functions
     * @return a dim-1 dimensional database where the objects are projected into the specified subspace
     * @throws UnableToComplyException if an error according to the database occurs
     */
    private Database<ParameterizationFunction> buildDB(int dim,
                                                       Matrix basis,
                                                       Set<Integer> ids,
                                                       Database<ParameterizationFunction> database) throws UnableToComplyException {
        // build objects and associations
        List<ObjectAndAssociations<ParameterizationFunction>> oaas = new ArrayList<ObjectAndAssociations<ParameterizationFunction>>(ids.size());

        for (Integer id : ids) {
            ParameterizationFunction f = project(basis, database.get(id));

            Associations associations = database.getAssociations(id);
            ObjectAndAssociations<ParameterizationFunction> oaa = new ObjectAndAssociations<ParameterizationFunction>(f, associations);
            oaas.add(oaa);
        }

        // insert into db
        Database<ParameterizationFunction> result = new SequentialDatabase<ParameterizationFunction>();
        result.insert(oaas);

        if (debug) {
            debugFine("\ndb fuer dim " + (dim - 1) + ": " + result.size());
        }

        return result;
    }

    /**
     * Projects the specified parametrization function into the subspace
     * described by the given basis.
     *
     * @param basis the basis defining he subspace
     * @param f     the parametrization function to be projected
     * @return the projected parametrization function
     */
    private ParameterizationFunction project(Matrix basis, ParameterizationFunction f) {
//    Matrix m = new Matrix(new double[][]{f.getPointCoordinates()}).times(basis);
        Matrix m = f.getRowVector().times(basis);
        ParameterizationFunction f_t = new ParameterizationFunction(m.getColumnPackedCopy());
        f_t.setID(f.getID());
        return f_t;
    }

    /**
     * Determines a basis defining a subspace described by the specified alpha values.
     *
     * @param alpha the alpha values
     * @return a basis defining a subspace described by the specified alpha values
     */
    private Matrix determineBasis(double[] alpha) {
        double[] nn = new double[alpha.length + 1];
        for (int i = 0; i < nn.length; i++) {
            double alpha_i = i == alpha.length ? 0 : alpha[i];
            nn[i] = sinusProduct(0, i, alpha) * StrictMath.cos(alpha_i);
        }
        Matrix n = new Matrix(nn, alpha.length + 1);
        return n.completeToOrthonormalBasis();
    }

    /**
     * Computes the product of all sinus values of the specified angles
     * from start to end index.
     *
     * @param start the index to start
     * @param end   the index to end
     * @param alpha the array of angles
     * @return the product of all sinus values of the specified angles
     *         from start to end index
     */
    private double sinusProduct(int start, int end, double[] alpha) {
        double result = 1;
        for (int j = start; j < end; j++) {
            result *= StrictMath.sin(alpha[j]);
        }
        return result;
    }

    /**
     * Determines the next ''best'' interval at maximum level, i.e. the next interval containing the
     * most unprocessed obejcts.
     *
     * @param heap the heap storing the intervals
     * @return the next ''best'' interval at maximum level
     */
    private CASHInterval determineNextIntervalAtMaxLevel(DefaultHeap<Integer, CASHInterval> heap) {
        CASHInterval next = doDetermineNextIntervalAtMaxLevel(heap);
        // noise path was chosen
        while (next == null) {
            if (heap.isEmpty()) {
                return null;
            }
            next = doDetermineNextIntervalAtMaxLevel(heap);
        }

        return next;
    }

    /**
     * Recursive helper method to determine the next ''best'' interval at maximum level,
     * i.e. the next interval containing the most unprocessed obejcts
     *
     * @param heap the heap storing the intervals
     * @return the next ''best'' interval at maximum level
     */
    private CASHInterval doDetermineNextIntervalAtMaxLevel(DefaultHeap<Integer, CASHInterval> heap) {
        CASHInterval interval = heap.getMinNode().getValue();
        int dim = interval.getDimensionality();
        while (true) {
            // max level is reached
            if (interval.getLevel() >= maxLevel && interval.getMaxSplitDimension() == dim) {
                return interval;
            }

            if (heap.size() % 10000 == 0 && isVerbose()) {
                verbose("heap size " + heap.size());
            }

            if (heap.size() >= 40000) {
                warning("Heap size > 50.000!!!");
                heap.clear();
                return null;
            }

            if (debug) {
                debugFiner("\nsplit " + interval.toString() + " " + interval.getLevel() + "-" + interval.getMaxSplitDimension());
            }
            interval.split();

            // noise
            if (!interval.hasChildren()) {
                return null;
            }

            CASHInterval bestInterval;
            if (interval.getLeftChild() != null && interval.getRightChild() != null) {
                int comp = interval.getLeftChild().compareTo(interval.getRightChild());
                if (comp < 0) {
                    bestInterval = interval.getRightChild();
                    heap.addNode(new DefaultHeapNode<Integer, CASHInterval>(interval.getLeftChild().priority(), interval.getLeftChild()));
                }
                else {
                    bestInterval = interval.getLeftChild();
                    heap.addNode(new DefaultHeapNode<Integer, CASHInterval>(interval.getRightChild().priority(), interval.getRightChild()));
                }
            }
            else if (interval.getLeftChild() == null) {
                bestInterval = interval.getRightChild();
            }
            else {
                bestInterval = interval.getLeftChild();
            }

            interval = bestInterval;
        }
    }

    /**
     * Returns the set of ids belonging to the specified database.
     *
     * @param database the database containing the parametrization functions.
     * @return the set of ids belonging to the specified database
     */
    private Set<Integer> getDatabaseIDs(Database<ParameterizationFunction> database) {
        Set<Integer> result = new HashSet<Integer>(database.size());
        for (Iterator<Integer> it = database.iterator(); it.hasNext();) {
            result.add(it.next());
        }
        return result;
    }

    /**
     * Determines the minimum and maximum function value of all parametrization functions
     * stored in the specified database.
     *
     * @param database       the database containing the parametrization functions.
     * @param dimensionality the dimensionality of the database
     * @return an array containing the minimum and maximum function value of all parametrization functions
     *         stored in the specified database
     */
    private double[] determineMinMaxDistance(Database<ParameterizationFunction> database, int dimensionality) {
        double[] min = new double[dimensionality - 1];
        double[] max = new double[dimensionality - 1];
        Arrays.fill(max, Math.PI);
        HyperBoundingBox box = new HyperBoundingBox(min, max);

        double d_min = Double.POSITIVE_INFINITY;
        double d_max = Double.NEGATIVE_INFINITY;
        for (Iterator<Integer> it = database.iterator(); it.hasNext();) {
            Integer id = it.next();
            ParameterizationFunction f = database.get(id);
            HyperBoundingBox minMax = f.determineAlphaMinMax(box);
            double f_min = f.function(minMax.getMin());
            double f_max = f.function(minMax.getMax());

            d_min = Math.min(d_min, f_min);
            d_max = Math.max(d_max, f_max);
        }
        return new double[]{d_min, d_max};
    }

    /**
     * Runs the derivator on the specified inerval and assigns all points
     * having a distance less then the standard deviation of the derivator model
     * to the model to this model.
     *
     * @param database the database containing the parametrization functions
     * @param interval the interval to build the model
     * @param dim      the dimensinality of the database
     * @param ids      an empty set to assign the ids
     * @return a basis of the found subspace
     * @throws UnableToComplyException if an error according to the database occurs
     * @throws ParameterException      if the parameter setting is wrong
     */
    private Matrix runDerivator(Database<ParameterizationFunction> database,
                                int dim,
                                CASHInterval interval,
                                Set<Integer> ids) throws UnableToComplyException, ParameterException {
        // build database for derivator
        Database<RealVector> derivatorDB = buildDerivatorDB(database, interval);

        DependencyDerivator derivator = new DependencyDerivator();
        // set the parameters
        List<String> parameters = new ArrayList<String>();
        Util.addParameter(parameters, OptionID.PCA_EIGENPAIR_FILTER, FirstNEigenPairFilter.class.getName());
        Util.addParameter(parameters, OptionID.EIGENPAIR_FILTER_N, Integer.toString(dim - 1));
        derivator.setParameters(parameters.toArray(new String[parameters.size()]));

        //noinspection unchecked
        derivator.run(derivatorDB);
        CorrelationAnalysisSolution model = derivator.getResult();

        Matrix weightMatrix = model.getSimilarityMatrix();
        RealVector centroid = new DoubleVector(model.getCentroid());
        //noinspection unchecked
        DistanceFunction<RealVector, DoubleDistance> df = new WeightedDistanceFunction(weightMatrix);
        DoubleDistance eps = df.valueOf("0.25");

        ids.addAll(interval.getIDs());
        for (Iterator<Integer> it = database.iterator(); it.hasNext();) {
            Integer id = it.next();
            RealVector v = new DoubleVector(database.get(id).getRowVector().getRowPackedCopy());
            DoubleDistance d = df.distance(v, centroid);
            if (d.compareTo(eps) < 0) {
                ids.add(id);
            }
        }

        Matrix basis = model.getStrongEigenvectors();
        return basis.getMatrix(0, basis.getRowDimensionality() - 1, 0, dim - 2);
    }

    /**
     * Builds a database for the derivator consisting of the ids
     * in the specified interval.
     *
     * @param database the database storing the paramterization functions
     * @param interval the interval to build the database from
     * @return a database for the derivator consisting of the ids
     *         in the specified interval
     * @throws UnableToComplyException if an error according to the database occurs
     */
    private Database<RealVector> buildDerivatorDB(Database<ParameterizationFunction> database,
                                                  CASHInterval interval) throws UnableToComplyException {
        // build objects and associations
        List<ObjectAndAssociations<RealVector>> oaas = new ArrayList<ObjectAndAssociations<RealVector>>(database.size());

        for (Integer id : interval.getIDs()) {
            Associations associations = database.getAssociations(id);
            RealVector v = new DoubleVector(database.get(id).getRowVector().getRowPackedCopy());
            ObjectAndAssociations<RealVector> oaa = new ObjectAndAssociations<RealVector>(v, associations);
            oaas.add(oaa);
        }

        // insert into db
        Database<RealVector> result = new SequentialDatabase<RealVector>();
        result.insert(oaas);

        if (debug) {
            debugFine("\ndb fuer derivator : " + result.size());
        }

        return result;
    }
}
