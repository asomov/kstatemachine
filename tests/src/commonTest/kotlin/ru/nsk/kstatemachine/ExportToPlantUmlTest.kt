package ru.nsk.kstatemachine

import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeInstanceOf
import ru.nsk.kstatemachine.HistoryType.DEEP
import ru.nsk.kstatemachine.visitors.exportToMermaid
import ru.nsk.kstatemachine.visitors.exportToPlantUml

private const val PLANTUML_NESTED_STATES_RESULT = """@startuml
hide empty description
state State1
state State3
state State2 {
    state Final_subState
    state Initial_subState
    
    [*] --> Initial_subState
    Initial_subState --> Final_subState
    Final_subState --> [*]
}

[*] --> State1
State1 --> State2 : to State2
State1 --> State1
State2 --> State3
State2 --> State1 : back
State3 --> [*]
@enduml
"""

private const val PLANTUML_NESTED_STATES_SHOW_EVENT_LABELS_RESULT = """@startuml
hide empty description
state State1
state State3
state State2 {
    state Final_subState
    state Initial_subState
    
    [*] --> Initial_subState
    Initial_subState --> Final_subState : SwitchEvent
    Final_subState --> [*]
}

[*] --> State1
State1 --> State2 : to State2, SwitchEvent
State1 --> State1 : SwitchEvent
State2 --> State3 : SwitchEvent
State2 --> State1 : back, SwitchEvent
State3 --> [*]
@enduml
"""

private const val MERMAID_NESTED_STATES_RESULT = """stateDiagram-v2
state State1
state State3
state State2 {
    state Final_subState
    state Initial_subState
    
    [*] --> Initial_subState
    Initial_subState --> Final_subState
    Final_subState --> [*]
}

[*] --> State1
State1 --> State2 : to State2
State1 --> State1
State2 --> State3
State2 --> State1 : back
State3 --> [*]
"""

private const val PLANTUML_PARALLEL_STATES_RESULT = """@startuml
hide empty description
state parallel_states {
    state "State 1" as State1 {
        state State_11
        state State12
        
        [*] --> State_11
        State_11 --> State12 : to State 12
        State12 --> State_11
    }
    --
    state State2 {
        state State21
        state State22
        
        [*] --> State21
        State21 --> State22
        State22 --> State21
    }
    
}

[*] --> parallel_states
@enduml
"""

private const val PLANTUML_PSEUDO_STATES_RESULT = """@startuml
hide empty description
state state1
state state2 {
    state state21 {
        state state211
        
        [*] --> state211
    }
    state state22
    
    [*] --> state21
}
state state3
state choice <<choice>>
state final

[*] --> state1
final --> [*]
state3 --> state2[H]
state3 --> state2[H*]
@enduml
"""

private const val PLANTUML_UNSAFE_PSEUDO_STATES_RESULT = """@startuml
hide empty description
state state1
state state2 {
    state state21 {
        state state211
        
        [*] --> state211
    }
    state state22
    
    [*] --> state21
}
state state3
state choice <<choice>>
state final

[*] --> state1
final --> [*]
choice --> state1
state3 --> state2[H]
state3 --> state2[H*]
@enduml
"""

private const val PLANTUML_COMPOSED_MACHINES_RESULT = """@startuml
hide empty description
state outer_state1
state inner_machine_StateMachine

[*] --> outer_state1
@enduml
"""

private const val PLANTUML_MULTIPLE_TARGET_STATES_RESULT = """@startuml
hide empty description
state state1
state state2 {
    state state21 {
        state state211
        state state212
        
        [*] --> state211
    }
    --
    state state22 {
        state state221
        state state222
        
        [*] --> state221
    }
    
}

[*] --> state1
state1 --> state212
state1 --> state222
@enduml
"""

private const val PLANTUML_META_INFO = """@startuml
hide empty description
state State_1 {
    state State12
    state "Choice label" as ChoiceState <<choice>>
    note right of ChoiceState : Note 1
    note right of ChoiceState : Note 2
    
    [*] --> ChoiceState
}
state "Long State 3" as State3
State3 : Description 1
State3 : Description 2
note right of State3 : Note 1
note right of State3 : Note 2
state "Long State 2" as State2 {
    state "Final sub state" as FinalState
    state Initial_subState
    
    [*] --> Initial_subState
    Initial_subState --> FinalState : SwitchEvent
    FinalState --> [*]
}

[*] --> State_1
State_1 --> State2 : go to State2, SwitchEvent
State_1 --> State_1 : self targeted, SwitchEvent
note on link
    Note 1
end note
note on link
    Note 2
end note
State2 --> State3 : That's all, SwitchEvent
State2 --> State_1 : back to State 1, SwitchEvent
State3 --> [*]
ChoiceState --> State12
@enduml
"""

private fun makeNestedMachine(coroutineStarterType: CoroutineStarterType): StateMachine {
    return createTestStateMachine(coroutineStarterType, name = "Nested states") {
        val state1 = initialState("State1")
        val state3 = finalState("State3")

        val state2 = state("State2") {
            transition<SwitchEvent> { targetState = state3 }
            transition<SwitchEvent>("back") { targetState = state1 }

            val finalSubState = finalState("Final subState")
            initialState("Initial subState") {
                transition<SwitchEvent> { targetState = finalSubState }
            }
        }

        state1 {
            transition<SwitchEvent>("to ${state2.name}") { targetState = state2 }
            transition<SwitchEvent> { targetState = this@state1 }
            transition<SwitchEvent>()
        }
    }
}

private fun makeChoiceMachine(coroutineStarterType: CoroutineStarterType): StateMachine {
    return createTestStateMachine(coroutineStarterType, enableUndo = true) {
        val state1 = initialState("state1")

        val state2 = state("state2") {
            initialState("state21") {
                initialState("state211")
            }
            state("state22")
        }
        val shallowHistory = state2.historyState("shallow history")
        val deepHistory = state2.historyState("deep history", historyType = DEEP)

        state("state3") {
            transition<FirstEvent>(targetState = shallowHistory)
            transition<SecondEvent>(targetState = deepHistory)
        }
        choiceState("choice") { state1 }
        finalState("final")
    }
}

class ExportToPlantUmlTest : StringSpec({
    CoroutineStarterType.entries.forEach { coroutineStarterType ->
        table(
            headers("showEventLabels", "result"),
            row(false, PLANTUML_NESTED_STATES_RESULT),
            row(true, PLANTUML_NESTED_STATES_SHOW_EVENT_LABELS_RESULT),
        ).forAll { showEventLabels, result ->
            "plantUml export nested states" {
                val machine = makeNestedMachine(coroutineStarterType)
                machine.exportToPlantUml(showEventLabels) shouldBe result
            }
        }

        "Mermaid export nested states" {
            val machine = makeNestedMachine(coroutineStarterType)
            machine.exportToMermaid() shouldBe MERMAID_NESTED_STATES_RESULT
        }

        "plantUml export parallel states" {
            val machine = createTestStateMachine(coroutineStarterType, name = "Parallel states") {
                initialState("parallel states", ChildMode.PARALLEL) {
                    state("State1") {
                        metaInfo = UmlMetaInfo("State 1")
                        val state11 = initialState("State-11")
                        val state12 = state("State12")

                        state11 {
                            transition<SwitchEvent> {
                                metaInfo = UmlMetaInfo("to State 12")
                                targetState = state12
                            }
                        }
                        state12 {
                            transition<SwitchEvent> { targetState = state11 }
                        }
                    }
                    state("State2") {
                        val state21 = initialState("State21")
                        val state22 = state("State22")

                        state21 {
                            transition<SwitchEvent> { targetState = state22 }
                        }
                        state22 {
                            transition<SwitchEvent> { targetState = state21 }
                        }
                    }
                }
            }

            machine.exportToPlantUml() shouldBe PLANTUML_PARALLEL_STATES_RESULT
        }

        "plantUml export with pseudo states" {
            val machine = makeChoiceMachine(coroutineStarterType)
            machine.exportToPlantUml() shouldBe PLANTUML_PSEUDO_STATES_RESULT
        }

        "plantUml unsafe export with pseudo states" {
            val machine = makeChoiceMachine(coroutineStarterType)
            machine.exportToPlantUml(unsafeCallConditionalLambdas = true) shouldBe PLANTUML_UNSAFE_PSEUDO_STATES_RESULT
        }

        "plantUml export composed machines" {
            val inner = createTestStateMachine(coroutineStarterType, name = "inner machine") {
                initialState("inner state1")
                state("inner state2")
            }
            val outer = createTestStateMachine(coroutineStarterType, name = "outer machine") {
                initialState("outer state1")
                addState(inner)
            }

            outer.exportToPlantUml() shouldBe PLANTUML_COMPOSED_MACHINES_RESULT
        }

        "plantUml export multiple target states" {
            lateinit var state212: State
            lateinit var state222: State

            val machine = createTestStateMachine(coroutineStarterType, "state machine") {
                initialState("state1") {
                    transitionConditionally<SwitchEvent> {
                        direction = {
                            event.shouldNotBeInstanceOf<SwitchEvent>() // ExportPlantUmlEvent is provided as a fake
                            targetParallelStates(state212, state222)
                        }
                    }
                }
                state("state2", childMode = ChildMode.PARALLEL) {
                    state("state21") {
                        initialState("state211")
                        state212 = state("state212")
                    }
                    state("state22") {
                        initialState("state221")
                        state222 = state("state222")
                    }
                }
            }

            machine.exportToPlantUml(unsafeCallConditionalLambdas = true) shouldBe PLANTUML_MULTIPLE_TARGET_STATES_RESULT
        }

        "metaInfo export test" {
            val machine = createStateMachine(this) {
                // label for state machine
                metaInfo = UmlMetaInfo("Nested states sm")

                val state1 = initialState("State-1")
                val state3 = finalState("State3") {
                    // label for state
                    metaInfo = UmlMetaInfo(
                        umlLabel = "Long State 3",
                        umlStateDescriptions = listOf("Description 1", "Description 2"),
                        umlNotes = listOf("Note 1", "Note 2"),
                    )
                }

                val state2 = state("State2") {
                    // label for state
                    metaInfo = UmlMetaInfo("Long State 2")
                    transition<SwitchEvent> {
                        // label for transition
                        metaInfo = UmlMetaInfo("That's all")
                        targetState = state3
                    }
                    transition<SwitchEvent>("back") {
                        // label for transition
                        metaInfo = UmlMetaInfo("back to State 1")
                        targetState = state1
                    }
                    val finalSubState = finalState("FinalState") {
                        // label for state
                        metaInfo = UmlMetaInfo("Final sub state")
                    }
                    initialState("Initial subState") {
                        transition<SwitchEvent> { targetState = finalSubState }
                    }
                }

                state1 {
                    transition<SwitchEvent> {
                        metaInfo = UmlMetaInfo("go to ${state2.name}")
                        targetState = state2
                    }
                    transition<SwitchEvent>("self targeted") {
                        targetState = this@state1
                        metaInfo = UmlMetaInfo(umlNotes = listOf("Note 1", "Note 2"))
                    }
                    transition<SwitchEvent>()

                    val state12 = state("State12")
                    val choiceState = initialChoiceState("ChoiceState") { state12 }
                    choiceState.metaInfo = UmlMetaInfo(
                        umlLabel = "Choice label",
                        // no plantUml nor Mermaid can draw this
                        umlStateDescriptions = listOf("Description 1", "Description 2"),
                        umlNotes = listOf("Note 1", "Note 2")
                    )
                }
            }

            machine.exportToPlantUml(
                showEventLabels = true,
                unsafeCallConditionalLambdas = true
            ) shouldBe PLANTUML_META_INFO
        }
    }
})