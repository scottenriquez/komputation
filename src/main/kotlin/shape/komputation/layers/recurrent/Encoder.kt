package shape.komputation.layers.recurrent

import shape.komputation.functions.activation.ActivationFunction
import shape.komputation.functions.extractStep
import shape.komputation.initialization.InitializationStrategy
import shape.komputation.layers.ContinuationLayer
import shape.komputation.layers.OptimizableLayer
import shape.komputation.layers.feedforward.activation.*
import shape.komputation.matrix.*
import shape.komputation.optimization.OptimizationStrategy
import java.util.*

class Encoder(
    name : String?,
    private val emitOutputAtEachStep : Boolean,
    private val numberSteps: Int,
    private val inputRows: Int,
    private val hiddenDimension : Int,
    private val inputProjection: SeriesProjection,
    private val previousStateProjection: SeriesProjection,
    private val activationLayers: Array<ContinuationLayer>,
    private val bias : SeriesBias?) : ContinuationLayer(name), OptimizableLayer {

    private var state = doubleZeroColumnVector(hiddenDimension)

    override fun forward(input: DoubleMatrix): DoubleMatrix {

        Arrays.fill(state.entries, 0.0)

        input as SequenceMatrix

        val output = zeroSequenceMatrix(if(emitOutputAtEachStep) numberSteps else 1, hiddenDimension, 1)

        for (indexStep in 0..numberSteps - 1) {

            val step = input.getStep(indexStep)

            // projected input = input weights * input
            val projectedInput = inputProjection.forwardStep(indexStep, step)
            val projectedInputEntries = projectedInput.entries

            // projected state = state weights * state
            val projectedState =  previousStateProjection.forwardStep(indexStep, this.state)
            val projectedStateEntries = projectedState.entries

            // addition = projected state + projected input
            val additionEntries = DoubleArray(hiddenDimension) { index ->

                projectedInputEntries[index] + projectedStateEntries[index]

            }

            // pre-activation = addition + bias
            val preActivation =

                if(this.bias == null) {

                    additionEntries

                }
                else {

                    this.bias.forwardStep(additionEntries)

                }


            // activation = activate(pre-activation)
            this.state = activationLayers[indexStep].forward(DoubleMatrix(hiddenDimension, 1, preActivation))

            if (emitOutputAtEachStep) {

                output.setStep(indexStep, this.state.entries)

            }
            else {

                if (indexStep == numberSteps - 1) {

                    output.setStep(0, this.state.entries)

                }

            }


        }

        return output

    }

    override fun backward(incoming: DoubleMatrix): DoubleMatrix {

        val seriesBackwardWrtInput = zeroSequenceMatrix(this.numberSteps, inputRows)

        var seriesChain : DoubleMatrix? = null

        for (indexStep in this.numberSteps - 1 downTo 0) {

            val isLastTimeStep = indexStep == this.numberSteps - 1

            val backwardState =

                if(emitOutputAtEachStep) {

                    val incomingStepGradient = extractStep(incoming.entries, indexStep, hiddenDimension)

                    // Add up the incoming gradients from the previous layer and the previous step

                    val addition =

                        if (isLastTimeStep) {

                            incomingStepGradient

                        }
                        // No need to add up anything since there was no previous step (in backpropagation)
                        else {

                            val seriesChainEntries = seriesChain!!.entries

                            DoubleArray(hiddenDimension) { index ->

                                incomingStepGradient[index] + seriesChainEntries[index]

                            }

                        }

                        DoubleMatrix(inputRows, 1, addition)

                }
                else {

                    if (isLastTimeStep) {

                        incoming

                    }
                    else {

                        seriesChain!!

                    }


                }

            // d activate(state weights * state(1) + input weights * input(2) + bias)) / d state weights * state(1) + input weights * input(2) + bias
            val backwardStateWrtStatePreActivation = this.activationLayers[indexStep].backward(backwardState)

            // d state weights * state(1) + input weights * input(2) + bias / d state(1) = state weights
            seriesChain = this.previousStateProjection.backwardStep(indexStep, backwardStateWrtStatePreActivation)

            // d state weights * state(1) + input weights * input(2) + bias / d input(2) = input weights
            val backwardStatePreActivationWrtInput = this.inputProjection.backwardStep(indexStep, backwardStateWrtStatePreActivation)

            if (this.bias != null) {

                // d state weights * state(1) + input weights * input(2) + bias / d bias = 1
                this.bias.backwardStep(backwardStateWrtStatePreActivation)

            }

            seriesBackwardWrtInput.setStep(indexStep, backwardStatePreActivationWrtInput.entries)

        }

        this.previousStateProjection.backwardSeries()
        this.inputProjection.backwardSeries()

        if (this.bias != null) {

            this.bias.backwardSeries()

        }

        return seriesChain!!

    }

    override fun optimize() {

        this.previousStateProjection.optimize()
        this.inputProjection.optimize()

        if (this.bias != null) {

            this.bias.optimize()

        }

    }

}

fun createEncoder(
    emitOutputAtEachStep: Boolean,
    numberSteps : Int,
    numberStepRows : Int,
    hiddenDimension: Int,
    activationFunction : ActivationFunction,
    inputWeightInitializationStrategy: InitializationStrategy,
    stateWeightInitializationStrategy: InitializationStrategy,
    biasInitializationStrategy: InitializationStrategy?,
    optimizationStrategy : OptimizationStrategy? = null) =

    createEncoder(
        null,
        emitOutputAtEachStep,
        numberSteps,
        numberStepRows,
        hiddenDimension,
        activationFunction,
        inputWeightInitializationStrategy,
        stateWeightInitializationStrategy,
        biasInitializationStrategy,
        optimizationStrategy)

fun createEncoder(
    name : String?,
    emitOutputAtEachStep: Boolean,
    numberSteps : Int,
    inputDimension : Int,
    hiddenDimension: Int,
    activationFunction : ActivationFunction,
    inputProjectionInitializationStrategy: InitializationStrategy,
    previousStateProjectionInitializationStrategy: InitializationStrategy,
    biasInitializationStrategy: InitializationStrategy?,
    optimizationStrategy : OptimizationStrategy? = null): Encoder {

    val inputProjectionName = if(name == null) null else "$name-input-projection"
    val inputProjection = createSeriesProjection(inputProjectionName, numberSteps, false, hiddenDimension, inputDimension, inputProjectionInitializationStrategy, optimizationStrategy)

    val previousStateProjectionName = if(name == null) null else "$name-previous-state-projection"
    val previousStateProjection = createSeriesProjection(previousStateProjectionName, numberSteps, true, hiddenDimension, hiddenDimension, previousStateProjectionInitializationStrategy, optimizationStrategy)

    val activationName = if(name == null) null else "$name-state-activation"
    val activationLayers = createActivationLayers(activationName, numberSteps, activationFunction)

    val bias =

        if(biasInitializationStrategy == null)
            null
        else
            createSeriesBias(if(name == null) null else "$name-bias", hiddenDimension, biasInitializationStrategy, optimizationStrategy)

    val encoder = Encoder(
        name,
        emitOutputAtEachStep,
        numberSteps,
        inputDimension,
        hiddenDimension,
        inputProjection,
        previousStateProjection,
        activationLayers,
        bias
    )

    return encoder

}
