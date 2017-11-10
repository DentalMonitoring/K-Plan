package com.dentalmonitoring.kplan.test

import com.dentalmonitoring.kplan.GraphvizGenerator
import com.dentalmonitoring.kplan.PresenceChecker
import com.dentalmonitoring.kplan.model.Instance
import com.dentalmonitoring.kplan.model.InstanceId
import com.dentalmonitoring.kplan.model.State
import com.dentalmonitoring.kplan.model.StateId
import com.dentalmonitoring.kplan.test.utils.KPlanSpecsHelper
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.util.*

object GraphvizTest : Spek({
    describe("a workflow renderer") {
        val helper = KPlanSpecsHelper(this)
        val kplan = helper.newKplan()

        // parentStart -> A -> B -> Z
        given("a linear workflow") {
            val simpleWorkflowName = "simple"

            val linearWorkflow = kplan.new<Unit, Unit>(simpleWorkflowName) {
                val A = businessTask<Unit, String>("A")
                val B = businessTask<String, Unit>("B")
                val Z = normalEndTask<Unit>("Z")

                start with A then B then Z
            }

            given("no workflow instance") {
                on("rendering the workflow") {
                    val generateGraphViz = GraphvizGenerator().generateGraphViz(linearWorkflow)

                    it("should be well printed") {
                        generateGraphViz shouldMatch equalTo("""|digraph "workflow" {
                                                                |    compound=true;
                                                                |    rankdir=LR;
                                                                |    nodesep=0.6;
                                                                |    ranksep=0.8;
                                                                |    pad=0.4;
                                                                |    "start" [shape=doublecircle,label=""];
                                                                |    "start" -> "@A" [style=bold];
                                                                |    "@A" [shape=box,style=bold,label="A"];
                                                                |    "@A" -> "@B" [style=bold];
                                                                |    "@B" [shape=box,style=bold,label="B"];
                                                                |    "@B" -> "@Z" [style=bold];
                                                                |    "@Z" [shape=ellipse,label="Z"];
                                                                |}
                                                                |""".trimMargin())
                    }
                }
            }

            given("an instance of the given workflow and its current state 'B'") {
                val instanceId = InstanceId("simple12345")

                val statesInfo: List<State.Info> = StatesInfoBuilder()
                    .withPastStateInfo("A", instanceId, instanceId)
                    .withCurrentStateInfo("B", instanceId, instanceId)
                    .build()
                val presenceChecker = PresenceChecker(statesInfo,
                    listOf(Instance.Info(instanceId, instanceId, simpleWorkflowName, StateId("anyStartId"))))

                on("rendering the workflow") {
                    val generateGraphViz = GraphvizGenerator(presenceChecker).generateGraphViz(linearWorkflow)

                    it("should be well printed with 'A' in blue, 'B' in pink") {
                        generateGraphViz shouldMatch equalTo("""|digraph "workflow" {
                                                                |    compound=true;
                                                                |    rankdir=LR;
                                                                |    nodesep=0.6;
                                                                |    ranksep=0.8;
                                                                |    pad=0.4;
                                                                |    "start" [shape=doublecircle,label=""];
                                                                |    "start" -> "@A" [style=bold];
                                                                |    "@A" [style=filled,label="A",shape=box,color=lightblue];
                                                                |    "@A" -> "@B" [style=bold];
                                                                |    "@B" [style=filled,label="B",shape=box,color=lightpink];
                                                                |    "@B" -> "@Z" [style=bold];
                                                                |    "@Z" [shape=ellipse,label="Z"];
                                                                |}
                                                                |""".trimMargin())
                    }
                }
            }
        }

        // parentStart -> [ subStart -> A -> X -> Z ] -> B -> X -> Z
        given("a workflow including a sub workflow sharing the same task 'X'") {
            val parentWorkflowName = "parentOfSub"
            val childWorkflowName = "parent-child"

            val parentWorkflow = kplan.new<Unit, String>(parentWorkflowName) {
                val sub = workflowTaskFromInstance<String>("child", kplan.new(childWorkflowName) {
                    val A = businessTask<Unit, String>("A")
                    val X = businessTask<String, String>("X")
                    val Z = normalEndTask<String>("Z")

                    start with A then X then Z
                })

                val B = businessTask<String, String>("B")
                val X = businessTask<String, String>("X")
                val Z = normalEndTask<String>("Z")

                start with sub then B then X then Z

            }

            given("no workflow instance") {
                on("rendering the workflow") {
                    val generateGraphViz = GraphvizGenerator().generateGraphViz(parentWorkflow)

                    it("should be well printed") {
                        generateGraphViz shouldMatch equalTo("""|digraph "workflow" {
                                                                |    compound=true;
                                                                |    rankdir=LR;
                                                                |    nodesep=0.6;
                                                                |    ranksep=0.8;
                                                                |    pad=0.4;
                                                                |    "start" [shape=doublecircle,label=""];
                                                                |    "start" -> ":child-start" [style=bold,lhead="cluster:child"];
                                                                |    "@B" [shape=box,style=bold,label="B"];
                                                                |    "@B" -> "@X" [style=bold];
                                                                |    "@X" [shape=box,style=bold,label="X"];
                                                                |    "@X" -> "@Z" [style=bold];
                                                                |    "@Z" [shape=ellipse,label="Z"];
                                                                |    subgraph "cluster:child" {
                                                                |        label="child";
                                                                |        ":child-start" [shape=doublecircle,label=""];
                                                                |        ":child-start" -> ":child@A" [style=bold];
                                                                |        ":child@A" [shape=box,style=bold,label="A"];
                                                                |        ":child@A" -> ":child@X" [style=bold];
                                                                |        ":child@X" [shape=box,style=bold,label="X"];
                                                                |        ":child@X" -> ":child@Z" [style=bold];
                                                                |        ":child@Z" [shape=ellipse,label="Z"];
                                                                |    }
                                                                |    ":child@Z" -> "@B" [ltail="cluster:child",style=bold];
                                                                |}
                                                                |""".trimMargin())
                    }
                }
            }

            given("an instance of the given workflow and its current state 'B") {
                val parentInstanceId = InstanceId("parent123")
                val childInstanceId = InstanceId("child123")

                val statesInfo: List<State.Info> = StatesInfoBuilder()
                    .withPastStateInfo("child", parentInstanceId, parentInstanceId)
                    .withPastStateInfo("A", childInstanceId, parentInstanceId)
                    .withPastStateInfo("X", childInstanceId, parentInstanceId)
                    .withCurrentStateInfo("B", parentInstanceId, parentInstanceId)
                    .build()
                val presenceChecker = PresenceChecker(statesInfo,
                    listOf(Instance.Info(parentInstanceId, parentInstanceId, parentWorkflowName, StateId("anyStartId")),
                        Instance.Info(childInstanceId, parentInstanceId, childWorkflowName, StateId("anyStartId"), InstanceId("azer"), StateId("id-child"))))

                on("rendering the workflow") {
                    val generateGraphViz = GraphvizGenerator(presenceChecker).generateGraphViz(parentWorkflow)

                    it("should be well printed with 'A' and first 'X' (from child workflow) in blue, 'B' in pink (and 'X' from parent workflow unchanged)") {
                        generateGraphViz shouldMatch equalTo("""|digraph "workflow" {
                                                                |    compound=true;
                                                                |    rankdir=LR;
                                                                |    nodesep=0.6;
                                                                |    ranksep=0.8;
                                                                |    pad=0.4;
                                                                |    "start" [shape=doublecircle,label=""];
                                                                |    "start" -> ":child-start" [style=bold,lhead="cluster:child"];
                                                                |    "@B" [style=filled,label="B",shape=box,color=lightpink];
                                                                |    "@B" -> "@X" [style=bold];
                                                                |    "@X" [shape=box,style=bold,label="X"];
                                                                |    "@X" -> "@Z" [style=bold];
                                                                |    "@Z" [shape=ellipse,label="Z"];
                                                                |    subgraph "cluster:child" {
                                                                |        label="child";
                                                                |        ":child-start" [shape=doublecircle,label=""];
                                                                |        ":child-start" -> ":child@A" [style=bold];
                                                                |        ":child@A" [style=filled,label="A",shape=box,color=lightblue];
                                                                |        ":child@A" -> ":child@X" [style=bold];
                                                                |        ":child@X" [style=filled,label="X",shape=box,color=lightblue];
                                                                |        ":child@X" -> ":child@Z" [style=bold];
                                                                |        ":child@Z" [shape=ellipse,label="Z"];
                                                                |    }
                                                                |    ":child@Z" -> "@B" [ltail="cluster:child",style=bold];
                                                                |}
                                                                |""".trimMargin())
                    }
                }
            }
        }

        // parentStart -> [ subStart -> A -> B -> X ] -> C -> [ subStart -> A -> B -> X ] -> Z
        given("a workflow including a twice the same sub workflow") {
            val parentWorkflowName = "parentOfSubs"
            val subWorkflowName = "sub"

            val subWorkflow = kplan.new<Unit, String>(subWorkflowName) {
                val A = businessTask<Unit, String>("A")
                val B = businessTask<String, String>("B")
                val X = normalEndTask<String>("X")

                start with A then B then X
            }

            val parentWorkflow = kplan.new<Unit, String>(parentWorkflowName) {
                val sub1 = workflowTaskFromInstance<String>("child1", subWorkflow)
                val sub2 = workflowTaskFromInstance<String>("child2", subWorkflow)
                val C = businessTask<String, Unit>("C")
                val Z = normalEndTask<String>("Z")

                start with sub1 then C then sub2 then Z

            }

            given("no workflow instance") {
                on("rendering the workflow") {
                    val generateGraphViz = GraphvizGenerator().generateGraphViz(parentWorkflow)

                    it("should be well printed") {
                        generateGraphViz shouldMatch equalTo("""|digraph "workflow" {
                                                                |    compound=true;
                                                                |    rankdir=LR;
                                                                |    nodesep=0.6;
                                                                |    ranksep=0.8;
                                                                |    pad=0.4;
                                                                |    "start" [shape=doublecircle,label=""];
                                                                |    "start" -> ":child1-start" [style=bold,lhead="cluster:child1"];
                                                                |    "@C" [shape=box,style=bold,label="C"];
                                                                |    "@C" -> ":child2-start" [style=bold,lhead="cluster:child2"];
                                                                |    "@Z" [shape=ellipse,label="Z"];
                                                                |    subgraph "cluster:child2" {
                                                                |        label="child2";
                                                                |        ":child2-start" [shape=doublecircle,label=""];
                                                                |        ":child2-start" -> ":child2@A" [style=bold];
                                                                |        ":child2@A" [shape=box,style=bold,label="A"];
                                                                |        ":child2@A" -> ":child2@B" [style=bold];
                                                                |        ":child2@B" [shape=box,style=bold,label="B"];
                                                                |        ":child2@B" -> ":child2@X" [style=bold];
                                                                |        ":child2@X" [shape=ellipse,label="X"];
                                                                |    }
                                                                |    ":child2@X" -> "@Z" [ltail="cluster:child2",style=bold];
                                                                |    subgraph "cluster:child1" {
                                                                |        label="child1";
                                                                |        ":child1-start" [shape=doublecircle,label=""];
                                                                |        ":child1-start" -> ":child1@A" [style=bold];
                                                                |        ":child1@A" [shape=box,style=bold,label="A"];
                                                                |        ":child1@A" -> ":child1@B" [style=bold];
                                                                |        ":child1@B" [shape=box,style=bold,label="B"];
                                                                |        ":child1@B" -> ":child1@X" [style=bold];
                                                                |        ":child1@X" [shape=ellipse,label="X"];
                                                                |    }
                                                                |    ":child1@X" -> "@C" [ltail="cluster:child1",style=bold];
                                                                |}
                                                                |""".trimMargin())
                    }
                }
            }

            given("an instance of the given workflow and its current state 'A' from the second workflow (so we should distinguish identical task from different workflows)") {
                val parentInstanceId = InstanceId("parent123")
                val childInstanceId1 = InstanceId("child123-1")
                val childInstanceId2 = InstanceId("child123-2")

                val statesInfo: List<State.Info> = StatesInfoBuilder()
                    .withPastStateInfo("child1", parentInstanceId, parentInstanceId)
                    .withPastStateInfo("A", childInstanceId1, parentInstanceId)
                    .withPastStateInfo("B", childInstanceId1, parentInstanceId)
                    .withPastStateInfo("C", parentInstanceId, parentInstanceId)
                    .withCurrentStateInfo("child2", parentInstanceId, parentInstanceId)
                    .withCurrentStateInfo("A", childInstanceId2, parentInstanceId)
                    .build()
                val presenceChecker = PresenceChecker(statesInfo,
                    listOf(Instance.Info(parentInstanceId, parentInstanceId, parentWorkflowName, StateId("anyStartId")),
                        Instance.Info(childInstanceId1, parentInstanceId, subWorkflowName, StateId("anyStartId"), InstanceId("azera"), StateId("id-child1")),
                        Instance.Info(childInstanceId2, parentInstanceId, subWorkflowName, StateId("anyStartId"), InstanceId("azera"), StateId("id-child2"))))

                on("rendering the workflow") {
                    val generateGraphViz = GraphvizGenerator(presenceChecker).generateGraphViz(parentWorkflow)

                    it("should be well printed with 'A' and first 'X' (from child workflow) in blue, 'B' in pink (and 'X' from parent workflow unchanged)") {
                        generateGraphViz shouldMatch equalTo("""|digraph "workflow" {
                                                                |    compound=true;
                                                                |    rankdir=LR;
                                                                |    nodesep=0.6;
                                                                |    ranksep=0.8;
                                                                |    pad=0.4;
                                                                |    "start" [shape=doublecircle,label=""];
                                                                |    "start" -> ":child1-start" [style=bold,lhead="cluster:child1"];
                                                                |    "@C" [style=filled,label="C",shape=box,color=lightblue];
                                                                |    "@C" -> ":child2-start" [style=bold,lhead="cluster:child2"];
                                                                |    "@Z" [shape=ellipse,label="Z"];
                                                                |    subgraph "cluster:child2" {
                                                                |        label="child2";
                                                                |        ":child2-start" [shape=doublecircle,label=""];
                                                                |        ":child2-start" -> ":child2@A" [style=bold];
                                                                |        ":child2@A" [style=filled,label="A",shape=box,color=lightpink];
                                                                |        ":child2@A" -> ":child2@B" [style=bold];
                                                                |        ":child2@B" [shape=box,style=bold,label="B"];
                                                                |        ":child2@B" -> ":child2@X" [style=bold];
                                                                |        ":child2@X" [shape=ellipse,label="X"];
                                                                |    }
                                                                |    ":child2@X" -> "@Z" [ltail="cluster:child2",style=bold];
                                                                |    subgraph "cluster:child1" {
                                                                |        label="child1";
                                                                |        ":child1-start" [shape=doublecircle,label=""];
                                                                |        ":child1-start" -> ":child1@A" [style=bold];
                                                                |        ":child1@A" [style=filled,label="A",shape=box,color=lightblue];
                                                                |        ":child1@A" -> ":child1@B" [style=bold];
                                                                |        ":child1@B" [style=filled,label="B",shape=box,color=lightblue];
                                                                |        ":child1@B" -> ":child1@X" [style=bold];
                                                                |        ":child1@X" [shape=ellipse,label="X"];
                                                                |    }
                                                                |    ":child1@X" -> "@C" [ltail="cluster:child1",style=bold];
                                                                |}
                                                                |""".trimMargin())
                    }
                }
            }
        }
    }
}) {
    class StatesInfoBuilder() {
        val instanceInfo = ArrayList<State.Info>()

        fun withCurrentStateInfo(name: String, instanceId: InstanceId, rootInstanceId: InstanceId): StatesInfoBuilder {
            instanceInfo.add(State.Info(StateId("id-$name"), instanceId, rootInstanceId, name, Date(), null))
            return this
        }

        fun withPastStateInfo(name: String, instanceId: InstanceId, rootInstanceId: InstanceId): StatesInfoBuilder {
            instanceInfo.add(State.Info(StateId("id-$name"), instanceId, rootInstanceId, name, Date(), Date()))
            return this
        }

        fun build() = instanceInfo
    }
}



