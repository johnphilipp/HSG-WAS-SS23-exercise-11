package tools;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.*;
import cartago.Artifact;
import cartago.OPERATION;
import cartago.OpFeedbackParam;

public class QLearner extends Artifact {

  private Lab lab; // the lab environment that will be learnt
  private int stateCount; // the number of possible states in the lab environment
  private int actionCount; // the number of possible actions in the lab environment
  private HashMap<String, double[][]> qTables = new HashMap<>(); // a map for storing the
    // qTables computed for different goals

  private static final int ITERATIONS = 100;
  private static final int MAX_REPETITIONS = 1000;
  private static final String FILENAME = "veryNiceQTable.ser";
  private final Random random = new Random();
  private static final Logger LOGGER = Logger.getLogger(QLearner.class.getName());

  public void init(String environmentURL) {

    // the URL of the W3C Thing Description of the lab Thing
    this.lab = new Lab(environmentURL);

    this.stateCount = this.lab.getStateCount();
    LOGGER.info("Initialized with a state space of n="+ stateCount);

    this.actionCount = this.lab.getActionCount();
    LOGGER.info("Initialized with an action space of m="+ actionCount);

    int currentState = this.lab.readCurrentState();
    Random random = new Random();
    for (int i = 0; i < ITERATIONS; i++) {
        List<Integer> possibleActions =
                this.lab.getApplicableActions(currentState);
        int randomAction =
                possibleActions.get(random.nextInt(possibleActions.size()));
        this.lab.performAction(randomAction);
    }
  }

/**
* Computes a Q matrix for the state space and action space of the lab, and against
* a goal description. For example, the goal description can be of the form [z1level, z2Level],
* where z1Level is the desired value of the light level in Zone 1 of the lab,
* and z2Level is the desired value of the light level in Zone 2 of the lab.
* For exercise 11, the possible goal descriptions are:
* [0,0], [0,1], [0,2], [0,3],
* [1,0], [1,1], [1,2], [1,3],
* [2,0], [2,1], [2,2], [2,3],
* [3,0], [3,1], [3,2], [3,3].
*
*<p>
* HINT: Use the methods of {@link LearningEnvironment} (implemented in {@link Lab})
* to interact with the learning environment (here, the lab), e.g., to retrieve the
* applicable actions, perform an action at the lab during learning etc.
*</p>
* @param  goalDescription  the desired goal against the which the Q matrix is calculated (e.g., [2,3])
* @param  episodesObj the number of episodes used for calculating the Q matrix
* @param  alphaObj the learning rate with range [0,1].
* @param  gammaObj the discount factor [0,1]
* @param epsilonObj the exploration probability [0,1]
* @param rewardObj the reward assigned when reaching the goal state
**/
  @OPERATION
  public void calculateQ(Object[] goalDescription , Object episodesObj, Object alphaObj, Object gammaObj, Object epsilonObj, Object rewardObj) {

    int episodes = Integer.parseInt(episodesObj.toString());
    double alpha = Double.parseDouble(alphaObj.toString());
    double gamma = Double.parseDouble(gammaObj.toString());
    double epsilon = Double.parseDouble(epsilonObj.toString());
    int reward = Integer.parseInt(rewardObj.toString());

    int z1 = Integer.parseInt(goalDescription[0].toString());
    int z2 = Integer.parseInt(goalDescription[1].toString());

    this.qTables = readQTable();

    String goal = String.valueOf(z1) + String.valueOf(z2);

    if (this.qTables.containsKey(goal)) {
      LOGGER.info("** QTable for goal description " + goal + " already exists**");
    } else {
      this.qTables.put(goal, initializeQTable());
      trainQTable(goal, episodes, alpha, gamma, epsilon, reward, z1, z2);
      saveQTable(qTables);
      LOGGER.info("Finished learning for goal description " + goal);
    }
  }

  /**
   * Helper methods
   *
   */

  private void trainQTable(String goal, int episodes, double alpha, double gamma, double epsilon, int reward, int z1, int z2) {
    double[][] currentQTable = this.qTables.get(goal);
    int currentState = this.lab.readCurrentState();
    Random random = new Random();
    for (int i = 0; i < episodes; i++) {
      LOGGER.info("Episode " + (i+1) + " / " + episodes);

      for (int j = 0; j < MAX_REPETITIONS; j++) {
        List<Integer> possibleActions = this.lab.getApplicableActions(currentState);
        int randomAction = possibleActions.get(random.nextInt(possibleActions.size()));
        this.lab.performAction(randomAction);
        sleep(2);
      }

      while (true) {
        List<Integer> possibleActions = this.lab.getApplicableActions(currentState);
        int chosenAction = possibleActions.get(random.nextInt(possibleActions.size()));

        if (random.nextDouble() > epsilon) {
          chosenAction = getMaxValueIndex(currentState, currentQTable[currentState]);
        }

        this.lab.performAction(chosenAction);
        sleep(2);
        int newState = this.lab.readCurrentState();
        double maxqsda = getMaxQSA(newState, currentQTable);
        double currentQsa = currentQTable[currentState][chosenAction];
        int calculatedReward = checkforReward(reward, z1, z2);
        double newValue = currentQsa + alpha * ((calculatedReward + gamma * maxqsda) - currentQsa);
        currentQTable[currentState][chosenAction] = newValue;
        currentState = newState;

        if (calculatedReward == reward) {
          break;
        }
      }
    }
  }

    private int checkforReward(int reward, Integer z1, Integer z2) {
    Integer[] observedGoalDescription = this.lab.getPossibleGoalDescription();
    if (Objects.equals(z1, observedGoalDescription[0]) && Objects.equals(z2, observedGoalDescription[1])) {
      LOGGER.info("> Rewarded");
      return reward;
    } else {
      return 0;
    }
  }

  private double getMaxQSA(int currentState,  double[][] qTable) {
    List<Integer> possibleActions = this.lab.getApplicableActions(currentState);
    double max = 0.0;
    for (int i = 0; i < possibleActions.size(); i++) {
      Integer possAct = possibleActions.get(i);
      double possibleMax = qTable[currentState][possAct];
      if (possibleMax > max) {
        max = possibleMax;
      }
    }
    return max;
  }

  private void sleep(int milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.warning("Thread sleep was interrupted.");
    }
  }


  /**
   * Simple read and save
   *
   */

  private static HashMap<String, double[][]> readQTable() {
    try (ObjectInputStream inputStream = new ObjectInputStream(Files.newInputStream(Paths.get(QLearner.FILENAME)))) {
      HashMap<String, double[][]> qTables = (HashMap<String, double[][]>) inputStream.readObject();
      System.out.println("Read QTables from: " + QLearner.FILENAME);
      return qTables;
    } catch (IOException | ClassNotFoundException e) {
      e.printStackTrace();
    }
    return new HashMap<>();
  }

  private static void saveQTable(HashMap<String, double[][]> qTables) {
    try (ObjectOutputStream outputStream = new ObjectOutputStream(Files.newOutputStream(Paths.get(QLearner.FILENAME)))) {
      outputStream.writeObject(qTables);
      System.out.println("Saved QTables to " + QLearner.FILENAME);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  /**
   * Agent artfacts
   *
   */

  @OPERATION
  public void getCurrentState(OpFeedbackParam<Integer[]> currentStateTag) {
    List<Integer> fullCurrentState = this.lab.getFullCurrentState();

    Integer[] currentState = new Integer[2];
    currentState[0] = fullCurrentState.get(0);
    currentState[1] = fullCurrentState.get(1);

    currentStateTag.set(currentState);
  }

  @OPERATION
  public void getCurrentFullState(OpFeedbackParam<Object[]> currentStateTag) {
    List<Integer> fullCurrentState = this.lab.getFullCurrentState();

    Object[] currentState = new Object[7];
    currentState[0] = fullCurrentState.get(0);
    currentState[1] = fullCurrentState.get(1);
    currentState[2] = fullCurrentState.get(2).equals(1);
    currentState[3] = fullCurrentState.get(3).equals(1);
    currentState[4] = fullCurrentState.get(4).equals(1);
    currentState[5] = fullCurrentState.get(5).equals(1);
    currentState[6] = fullCurrentState.get(6);

    currentStateTag.set(currentState);
  }




  /**
* Returns information about the next best action based on a provided state and the QTable for
* a goal description. The returned information can be used by agents to invoke an action
* using a ThingArtifact.
*
* @param  goalDescription  the desired goal against the which the Q matrix is calculated (e.g., [2,3])
* @param  currentStateDescription the current state e.g. [2,2,true,false,true,true,2]
* @param  nextBestActionTag the (returned) semantic annotation of the next best action, e.g. "http://example.org/was#SetZ1Light"
* @param  nextBestActionPayloadTags the (returned) semantic annotations of the payload of the next best action, e.g. [Z1Light]
* @param nextBestActionPayload the (returned) payload of the next best action, e.g. true
**/
  @OPERATION
  public void getActionFromState(Object[] goalDescription, Object[] currentStateDescription,
      OpFeedbackParam<String> nextBestActionTag, OpFeedbackParam<Object[]> nextBestActionPayloadTags,
      OpFeedbackParam<Object[]> nextBestActionPayload) {

    int z1 = Integer.parseInt(goalDescription[0].toString());
    int z2 = Integer.parseInt(goalDescription[1].toString());

    String newKey = z1 + "" + z2;
    double[][] currentQTable = this.qTables.get(newKey);
    int currentIndex = this.lab.readCurrentState();

    double epsilon = 0.9;
    double[] possibleActions = currentQTable[currentIndex];
    List<Integer> applicableActions = this.lab.getApplicableActions(currentIndex);

    int nextAction = applicableActions.get(random.nextInt(applicableActions.size()));
    if (random.nextDouble() > epsilon) {
      nextAction = getMaxValueIndex(currentIndex, possibleActions);
    }

    Runnable[] actions = {
            () -> setAction(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload, "http://example.org/was#SetZ1Light", new Object[]{"Z1Light"}, new Object[]{false}),
            () -> setAction(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload, "http://example.org/was#SetZ1Light", new Object[]{"Z1Light"}, new Object[]{true}),
            () -> setAction(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload, "http://example.org/was#SetZ2Light", new Object[]{"Z2Light"}, new Object[]{false}),
            () -> setAction(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload, "http://example.org/was#SetZ2Light", new Object[]{"Z2Light"}, new Object[]{true}),
            () -> setAction(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload, "http://example.org/was#SetZ1Blinds", new Object[]{"Z1Blinds"}, new Object[]{false}),
            () -> setAction(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload, "http://example.org/was#SetZ1Blinds", new Object[]{"Z1Blinds"}, new Object[]{true}),
            () -> setAction(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload, "http://example.org/was#SetZ2Blinds", new Object[]{"Z2Blinds"}, new Object[]{false}),
            () -> setAction(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload, "http://example.org/was#SetZ2Blinds", new Object[]{"Z2Blinds"}, new Object[]{true}),
    };

    if (nextAction >= 0 && nextAction < actions.length) {
      actions[nextAction].run();
    }
  }

  private void setAction(OpFeedbackParam<String> actionTag, OpFeedbackParam<Object[]> actionPayloadTags,
                         OpFeedbackParam<Object[]> actionPayload, String tag, Object[] payloadTags, Object[] payload) {
    actionTag.set(tag);
    actionPayloadTags.set(payloadTags);
    actionPayload.set(payload);
  }

  private int getMaxValueIndex(int currentState, double[] possibleActions) {
    List<Integer> applicableActions = this.lab.getApplicableActions(currentState);
    int maxIndex = 0;
    double maxValue = possibleActions[applicableActions.get(0)];

    for (int i = 1; i < applicableActions.size(); i++) {
      if (possibleActions[applicableActions.get(i)] > maxValue) {
        maxValue = possibleActions[applicableActions.get(i)];
        maxIndex = i;
      }
    }

    return maxIndex;
  }

  /**
   * Print the Q matrix
   *
   * @param qTable the Q matrix
   */
  void printQTable(double[][] qTable) {
    System.out.println("Q matrix");
    for (int i = 0; i < qTable.length; i++) {
      System.out.print("From state " + i + ":  ");
      for (int j = 0; j < qTable[i].length; j++) {
        System.out.printf("%6.2f ", (qTable[i][j]));
      }
      System.out.println();
    }
  }

  /**
   * Initialize a Q matrix
   *
   * @return the Q matrix
   */
  private double[][] initializeQTable() {
    double[][] qTable = new double[this.stateCount][this.actionCount];
    for (int i = 0; i < stateCount; i++){
      for(int j = 0; j < actionCount; j++){
        qTable[i][j] = 0.0;
      }
    }
    return qTable;
  }
}
