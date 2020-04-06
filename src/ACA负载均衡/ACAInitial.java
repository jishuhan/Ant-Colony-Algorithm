package ACA负载均衡;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author Childe-H
 * @Address SEU
 * @Created_Date 2020/4/5 - 10:06
 */
public class ACAInitial {

    int taskNum;                    // 任务数量

    int[] tasks;                    // 存储每个任务的长度

    int nodeNum;                    // 服务器数量

    int[] nodes;                    // 存储服务器处理速度

    int iteratorNum;                // 迭代次数

    int antNum;                     // 每次迭代中蚂蚁数量,每只蚂蚁都是一个任务调度者,每次迭代中的每只蚂蚁都需要完成所有的任务分配,也就是可行解

    /*
     * 结果集,存放处理完全部任务后,每个处理器的耗时;
     * 若算法收敛,则结果集中每个resultData[]应该逐渐趋于一致
     */
    double[][] resultData;

    /*
     * 任务处理时间矩阵
     *
     * [i][j]表示第i个任务分配给第j个处理器处理所需时间;
     * 基于tasks和nodes数组计算而来
     */
    double[][] timeMatrix;

    /*
     * 信息素矩阵
     *
     * 记录任务i分配给处理器j i->j这条路径上的信息素浓度
     * tip*********************************************************************
     * 注意：我们将负载均衡调度过程中的一次任务分配当作蚁群算法中一条路径。      *
     * 如：我们将“任务i分配给节点j”这一动作，当作蚂蚁从任务i走向节点j的一条路径。*
     * 因此，pheromoneMatrix[i][j]就相当于i——>j这条路径上的信息素浓度。         *
     */
    double[][] pheromoneMatrix;

    /*
     * 记录pheromoneMatrix中每一行中最大信息素的下标;
     * 一行表示一个任务task,该矩阵记录了在当前状态下,耗时最短的分配任务到处理器的方式
     */
    int[] maxPheromoneMatrix;

    /*
     * 在一次迭代中,采用随机分配策略的蚂蚁的临界编号
     *
     * 比如,将antNum设为10,那么每次迭代中都有10只蚂蚁完成所有的任务分配工作.
     * 并且分配过程按照蚂蚁编号从小到大顺序(编号从0开始).
     *
     * criticalPointMatrix[j]=5 : 分配第j个任务的时候,编号是0~5的蚂蚁根据
     * 信息素浓度进行任务分配(分配给本行信息素浓度最高的node处理),而6~9号蚂蚁
     * 采用随机分配方式,即随机分配给任一node处理
     *
     * 为什么这么做?
     *    如果每只蚂蚁都将任务分配给信息素浓度最高的节点处理,那么就会出现"停滞".
     *    也就是算法过早收敛于一个局部最优解.
     *    因此需要一部分蚂蚁遵循最高分配策略,另一部分用来发现更多的局部最优解
     *
     */
    int[] criticalPointMatrix;

    /*
     * p: 每次完成迭代后,信息素衰减比例(0 < p < 1)
     *    在真实蚁群中,蚂蚁分泌的信息素会随时间而衰减;在一次迭代中信息素浓度保持不变
     *
     * q: 蚂蚁每次经过一条路径,信息素增加比例(1 < q)
     *    每次迭代后,将蚂蚁经过的路径上增加信息素q;在一次迭代中信息素浓度保持不变
     */
    private final float p = 0.6f;
    private final float q = 1.5f;

    /**
     * 初始化
     */
    public ACAInitial(int taskNum, int nodeNum, int antNum, int iteratorNum) {

        this.taskNum = taskNum;
        this.nodeNum = nodeNum;
        this.antNum = antNum;
        this.iteratorNum = iteratorNum;
        /*任务长度为[10,100)随机数,处理速度为[10,100)随机数*/
        tasks = new int[taskNum];
        nodes = new int[nodeNum];
        for (int i = 0; i < taskNum; i++) {
            tasks[i] = (int) (Math.random() * 90 + 10);
        }
        for (int i = 0; i < nodeNum; i++) {
            nodes[i] = (int) (Math.random() * 90 + 10);
        }

        // 初始化任务执行时间矩阵 timeMatrix
        timeMatrix = new double[taskNum][nodeNum];
        initTimeMatrix();

        // 初始化信息素矩阵 pheromoneMatrix,用1填充
        pheromoneMatrix = new double[taskNum][nodeNum];
        initPheromoneMatrix();

        // 初始化随机蚂蚁编号数组 criticalPointMatrix
        criticalPointMatrix = new int[taskNum];

        // 初始化最大信息素矩阵 maxPheromoneMatrix
        maxPheromoneMatrix = new int[taskNum];
    }

    public void initTimeMatrix() {
        for (int i = 0, tasksLen = tasks.length; i < tasksLen; i++) {
            for (int j = 0, nodeLen = nodes.length; j < nodeLen; j++) {
                timeMatrix[i][j] = (double) tasks[i] / (double) nodes[j];
            }
        }
    }

    public void initPheromoneMatrix() {
        for (double[] ele : pheromoneMatrix) {
            Arrays.fill(ele, 1);
        }
    }


    /**
     * 将第taskCount个任务分配给某一个节点处理
     *
     * @param antCount  蚂蚁编号
     * @param taskCount 任务编号
     */
    public int assignOneTask(int antCount, int taskCount) {

        // 若当前蚂蚁编号在临界点之前，则采用最大信息素的分配方式
        if (antCount <= criticalPointMatrix[taskCount]) {
            return maxPheromoneMatrix[taskCount];
        }

        // 若当前蚂蚁编号在临界点之后，则采用随机分配方式 [0, nodeNum - 1]
        return (int) Math.ceil(Math.random() * (nodeNum - 1));
    }

    /**
     * 每完成一次迭代，计算本次迭代中所有蚂蚁的行走路径(即：所有蚂蚁的任务处理之间)，记录在time_allAnt矩阵中。
     * pathMatrix_allAnt[][][] 中的元素只包含0/1 。[i][j][k] == 1 :第i只蚂蚁,将第j个任务,交给了k处理器
     *
     * @param pathMatrix_allAnt 所有蚂蚁的路径
     */
    public double[] calTime_oneIt(int[][][] pathMatrix_allAnt) {

        // 所有蚂蚁处理任务的时间数组
        double[] time_allAnt = new double[antNum];

        int i = 0;

        for (int[][] pathMatrix_oneAnt : pathMatrix_allAnt) {

            // 获取第antIndex只蚂蚁的行走路径
            // 当前蚂蚁_oneAnt:获取处理时间最长的处理器nodeIndex 对应的处理时间
            double maxTime = -1;
            for (int nodeIndex = 0; nodeIndex < nodeNum; nodeIndex++) {

                // 计算节点taskIndex的任务处理时间
                double time = 0;
                for (int taskIndex = 0; taskIndex < taskNum; taskIndex++) {

                    // 若该路径被走过,即第taskIndex个任务被分配给了nodeIndex节点
                    if (pathMatrix_oneAnt[taskIndex][nodeIndex] == 1) {
                        time += timeMatrix[taskIndex][nodeIndex];
                    }
                }

                // 更新maxTime
                if (time > maxTime) {
                    maxTime = time;
                }
            }

            // 某只ant处理任务的时间,就是在该ant的"任务分配方式"下,耗时最长的处理器的时间
            time_allAnt[i++] = maxTime;
        }

        return time_allAnt;
    }

    /**
     * 更新信息素
     *
     * @param pathMatrix_allAnt 本次迭代中所有蚂蚁的行走路径
     * @param timeArray_oneIt   本次迭代的任务处理时间的结果集
     */
    public void updatePheromoneMatrix(int[][][] pathMatrix_allAnt, double[] timeArray_oneIt) {

        // 更新信息素矩阵 所有信息素均衰减p(0<p<1)
        for (int i = 0; i < taskNum; i++) {
            for (int j = 0; j < nodeNum; j++) {
                pheromoneMatrix[i][j] *= p;
            }
        }

        // 找出任务处理时间最短的蚂蚁编号
        double minTime = timeArray_oneIt[0];
        int minIndex = 0;
        for (int antIndex = 1; antIndex < antNum; antIndex++) {
            if (timeArray_oneIt[antIndex] < minTime) {
                minTime = timeArray_oneIt[antIndex];
                minIndex = antIndex;
            }
        }

        // 将本次迭代中最优路径的信息素增加q(q>1)
        for (int taskIndex = 0; taskIndex < taskNum; taskIndex++) {
            for (int nodeIndex = 0; nodeIndex < nodeNum; nodeIndex++) {
                // 在某只蚂蚁minIndex的分配方式下,处理总时间最小,那么对该只蚂蚁的路径,增加信息素
                if (pathMatrix_allAnt[minIndex][taskIndex][nodeIndex] == 1) {
                    pheromoneMatrix[taskIndex][nodeIndex] *= q;
                }
            }
        }


        for (int taskIndex = 0; taskIndex < taskNum; taskIndex++) {
            // 对任务进行循环

            double maxPheromone = pheromoneMatrix[taskIndex][0];
            int maxIndex = 0;
            double sumPheromone = pheromoneMatrix[taskIndex][0];
            boolean isAllSame = true;

            for (int nodeIndex = 1; nodeIndex < nodeNum; nodeIndex++) {

                // 找出当前任务taskIndex,使用nodeIndex处理器下,最大信息素及编号
                if (pheromoneMatrix[taskIndex][nodeIndex] > maxPheromone) {
                    maxPheromone = pheromoneMatrix[taskIndex][nodeIndex];
                    maxIndex = nodeIndex;
                }

                // for循环结束后,若将任务taskIndex给nodeIndex处理的信息素都相同,isAllSame为true
                if (pheromoneMatrix[taskIndex][nodeIndex] != pheromoneMatrix[taskIndex][nodeIndex - 1]) {
                    isAllSame = false;
                }

                sumPheromone += pheromoneMatrix[taskIndex][nodeIndex];
            }

            // 若本行信息素全都相等，则随机选择一个作为最大信息素
            // TODO 这里都相等则随机取一个,若部分值相等且为最大值呢
            if (isAllSame) {
                maxIndex = (int) (Math.random() * nodeNum);
                maxPheromone = pheromoneMatrix[taskIndex][maxIndex];
            }

            // 将本行最大信息素的下标加入maxPheromoneMatrix
            maxPheromoneMatrix[taskIndex] = maxIndex;

            // 将本次迭代的蚂蚁临界编号加入criticalPointMatrix(该临界点之前的蚂蚁的任务分配根据最大信息素,而临界点之后的蚂蚁采用随机分配)
            // 信息素总体会朝着最优方向趋动,当maxPheromone / sumPheromone == 1,则信息素集中于一点,此时
            criticalPointMatrix[taskIndex] = (int) Math.round(antNum * (maxPheromone / sumPheromone));
        }
    }

    /**
     * 迭代搜索
     */
    public double[][] acaSearch() {

        // 初始化resultData
        resultData = new double[iteratorNum][antNum];

        for (int itCount = 0; itCount < iteratorNum; itCount++) {

            // 本次迭代中，所有蚂蚁的路径
            int[][][] pathMatrix_allAnt = new int[antNum][taskNum][nodeNum];

            for (int antCount = 0; antCount < antNum; antCount++) {

                // 第antCount只蚂蚁的分配策略(pathMatrix[i][j]表示第antCount只蚂蚁将i任务分配给j节点处理)
                int[][] pathMatrix_oneAnt = new int[taskNum][nodeNum];
                for (int[] ele : pathMatrix_oneAnt) {
                    Arrays.fill(ele, 0);
                }

                for (int taskCount = 0; taskCount < taskNum; taskCount++) {
                    // 将第taskCount个任务分配给第nodeCount个节点处理
                    int nodeCount = assignOneTask(antCount, taskCount);
                    pathMatrix_oneAnt[taskCount][nodeCount] = 1;
                }

                // 将当前蚂蚁的路径加入pathMatrix_allAnt
                pathMatrix_allAnt[antCount] = pathMatrix_oneAnt;
            }

            // 计算 本次迭代中 所有蚂蚁 的任务处理时间
            double[] timeArray_oneIt = calTime_oneIt(pathMatrix_allAnt);

            // 将本地迭代中 所有蚂蚁的 任务处理时间加入总结果集
            resultData[itCount] = timeArray_oneIt;

            // 更新信息素
            updatePheromoneMatrix(pathMatrix_allAnt, timeArray_oneIt);
        }

        return resultData;
    }

    public static void main(String[] args) {

        ACAInitial acaInitial = new ACAInitial(100, 10, 10, 50);
        double[][] resultData = acaInitial.acaSearch();

        for (double[] res : resultData) {
            List<String> result = Arrays.stream(res)
                    .mapToObj(ele -> new DecimalFormat("#.000").format(ele))
                    .collect(Collectors.toList());

            System.out.println(result);
        }
        /*****************************************************************************************
         * 结果解读:
         * 若resultData中的数据趋于一致,说明算法收敛,
         * resultData中存放的是[nodeNum]个处理器处理完全部任务的耗时,耗时越一致,说明任务分配越"平均"
         * 若算法不收敛,可增加迭代次数iteratorNum */

    }
}


