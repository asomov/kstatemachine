package ru.nsk.kstatemachine

import ru.nsk.kstatemachine.ChildMode.EXCLUSIVE
import ru.nsk.kstatemachine.TransitionDirectionProducerPolicy.*

/**
 * The most common state
 */
open class DefaultState(name: String? = null, childMode: ChildMode = EXCLUSIVE) :
    BaseStateImpl(name, childMode), State

open class DefaultFinalState(name: String? = null) : DefaultState(name), FinalState

open class DefaultChoiceState(
    name: String? = null,
    private val choiceAction: suspend EventAndArgument<*>.() -> State
) : BasePseudoState(name), RedirectPseudoState {

    override suspend fun resolveTargetState(policy: TransitionDirectionProducerPolicy<*>): TransitionDirection {
        return internalResolveTargetState(policy, choiceAction)
    }
}

open class DefaultChoiceDataState<D : Any>(
    name: String? = null,
    private val choiceAction: suspend EventAndArgument<*>.() -> DataState<D>,
) : DataState<D>, BasePseudoState(name), RedirectPseudoState {

    override suspend fun resolveTargetState(policy: TransitionDirectionProducerPolicy<*>): TransitionDirection {
        return internalResolveTargetState(policy, choiceAction)
    }

    override val defaultData: D? = null
    override val data: D get() = error("PseudoState $this can not have data")
    override val lastData: D get() = error("PseudoState $this can not have lastData")
}

private suspend fun IState.internalResolveTargetState(
    policy: TransitionDirectionProducerPolicy<*>,
    choiceAction: suspend EventAndArgument<*>.() -> IState
): TransitionDirection {
    return when (policy) {
        is DefaultPolicy -> policy.targetState(
            policy.eventAndArgument.choiceAction().also { log { "$this resolved to $it" } }
        )
        is CollectTargetStatesPolicy -> noTransition()
        is UnsafeCollectTargetStatesPolicy -> policy.targetState(policy.eventAndArgument.choiceAction())
    }
}

open class BasePseudoState(name: String?) : BaseStateImpl(name, EXCLUSIVE), PseudoState {
    override suspend fun doEnter(transitionParams: TransitionParams<*>) = internalError()
    override suspend fun doExit(transitionParams: TransitionParams<*>) = internalError()

    override fun <L : IState.Listener> addListener(listener: L) =
        throw UnsupportedOperationException("PseudoState $this can not have listeners")

    override fun <S : IState> addState(state: S, init: StateBlock<S>?) =
        throw UnsupportedOperationException("PseudoState $this can not have child states")


    override fun <E : Event> addTransition(transition: Transition<E>) =
        throw UnsupportedOperationException("PseudoState $this can not have transitions")

    private fun internalError(): Nothing =
        error("Internal error, PseudoState $this can not be entered or exited, looks that machine is purely configured")
}