//illuminance controller agent

/*
* The URL of the W3C Web of Things Thing Description (WoT TD) of a lab environment
* Simulated lab WoT TD: "https://raw.githubusercontent.com/Interactions-HSG/example-tds/was/tds/interactions-lab.ttl"
* Real lab WoT TD: "https://raw.githubusercontent.com/Interactions-HSG/example-tds/was/tds/interactions-lab-real.ttl"
*/

/* Initial beliefs and rules */

// the agent has a belief about the location of the W3C Web of Thing (WoT) Thing Description (TD)
// that describes a lab environment to be learnt
learning_lab_environment("https://raw.githubusercontent.com/Interactions-HSG/example-tds/was/tds/interactions-lab.ttl").

// the agent believes that the task that takes place in the 1st workstation requires an indoor illuminance
// level of Rank 2, and the task that takes place in the 2nd workstation requires an indoor illumincance 
// level of Rank 3. Modify the belief so that the agent can learn to handle different goals.
task_requirements([2,3]).

/* Initial goals */
!start. // the agent has the goal to start

/* 
 * Plan for reacting to the addition of the goal !start
 * Triggering event: addition of goal !start
 * Context: the agent believes that there is a WoT TD of a lab environment located at Url, and that 
 * the tasks taking place in the workstations require indoor illuminance levels of Rank Z1Level and Z2Level
 * respectively
 * Body: (currently) creates a QLearnerArtifact and a ThingArtifact for learning and acting on the lab environment.
*/
@start
+!start : learning_lab_environment(Url) 
  & task_requirements([Z1Level, Z2Level]) <-

  .print("Hello world");
  .print("I want to achieve Z1Level=", Z1Level, " and Z2Level=",Z2Level);

  makeArtifact("qlearner", "tools.QLearner", [Url], QLearnerArtId);
  focus(QLearnerArtId);

  makeArtifact("lab", "wot.ThingArtifact", [Url], LabArtId);
  focus(LabArtId);

  calculateQ([2,1], 50, 0.2, 0.8, 0.2, 100)[artifact_id(QLearnerArtId)];

  getCurrentState(CurrentState)[artifact_id(QLearnerArtId)];
  +current_state(CurrentState);

  getCurrentFullState(CurrentFullState);
  +current_full_state(CurrentFullState);

  !exec(CurrentFullState).

  +!exec(CurrentFullState): current_state([Z1State, Z2State]) &
  task_requirements([Z1Goal, Z2Goal]) & Z1State == Z1Goal & Z2State == Z2Goal <-
    .print("Reached Goal State!").

  +!exec(CurrentFullState): true <-
    .print("CurrentFullState: ", CurrentFullState);

    getActionFromState([2,1], CurrentFullState, ActionTag, PayloadTags, Payload);
    invokeAction(ActionTag, PayloadTags, Payload);
    .abolish(current_state(_));

    getCurrentState(NewCurrentState)[artifact_id(QLearnerArtId)];
    +current_state(NewCurrentState);
    .print("Current State: ", NewCurrentState);

    ?task_requirements(Goal);
    .print("Goal State: ", Goal);

    getCurrentFullState(NewCurrentFullState);

    .wait(2000);

    !exec(NewCurrentFullState).
