package shape.komputation.layers.forward.decoder

import shape.komputation.functions.activation.ActivationFunction
import shape.komputation.functions.add
import shape.komputation.functions.extractStep
import shape.komputation.initialization.InitializationStrategy
import shape.komputation.layers.ForwardLayer
import shape.komputation.layers.concatenateNames
import shape.komputation.layers.forward.activation.ActivationLayer
import shape.komputation.layers.forward.activation.activationLayers
import shape.komputation.layers.forward.projection.SeriesBias
import shape.komputation.layers.forward.projection.SeriesWeighting
import shape.komputation.layers.forward.projection.seriesBias
import shape.komputation.layers.forward.projection.seriesWeighting
import shape.komputation.layers.forward.units.RecurrentUnit
import shape.komputation.matrix.DoubleMatrix
import shape.komputation.matrix.doubleColumnVector
import shape.komputation.matrix.doubleZeroColumnVector
import shape.komputation.matrix.doubleZeroMatrix
import shape.komputation.optimization.Optimizable
import shape.komputation.optimization.OptimizationStrategy


// The first input is empty.
// Starting with the second input, the input at step t is the output of step t-1.
class SingleInputDecoder internal constructor(
    name : String?,
    private val numberSteps : Int,
    private val outputDimension : Int,
    private val unit : RecurrentUnit,
    private val weighting: SeriesWeighting,
    private val bias: SeriesBias?,
    private val activations: Array<ActivationLayer>) : ForwardLayer(name), Optimizable {

    override fun forward(encoderOutput: DoubleMatrix, isTraining : Boolean): DoubleMatrix {

        val seriesOutput = doubleZeroMatrix(this.outputDimension, this.numberSteps)

        // Use the encoder output as the first state
        var state = encoderOutput
        // The first input is empty since there is no previous output
        var input = doubleZeroColumnVector(this.outputDimension)

        for (indexStep in 0..this.numberSteps - 1) {

            val newState = this.unit.forwardStep(indexStep, state, input, isTraining)

            val output = this.forwardOutput(indexStep, newState, isTraining)

            seriesOutput.setColumn(indexStep, output.entries)

            state = newState

            // The output of step t-1 is the input for step t.
            input = output

        }

        return seriesOutput

    }

    private fun forwardOutput(indexStep: Int, newState: DoubleMatrix, isTraining: Boolean): DoubleMatrix {

        val weighting = this.weighting.forwardStep(indexStep, newState, isTraining)

        val biased =

            if (this.bias != null) {

                this.bias.forwardStep(weighting)

            }
            else {

                weighting
            }

        val output = this.activations[indexStep].forward(biased, isTraining)

        return output

    }

    // Incoming gradient: d chain / d series prediction
    override fun backward(chain: DoubleMatrix): DoubleMatrix {

        val chainEntries = chain.entries

        // Differentiate the chain w.r.t. input
        var diffStatePreActivationWrtInput : DoubleMatrix? = null

        // Differentiate the chain w.r.t previous state.
        // This is done at each step. For the first step (t=1), the chain is differentiated w.r.t. to the initial state (t=0).
        var diffStatePreActivationWrtPreviousState : DoubleMatrix? = null

        for (indexStep in this.numberSteps - 1 downTo 0) {

            val chainStep = extractStep(chainEntries, indexStep, outputDimension)

            val diffOutputPreActivationWrtState = backwardOutput(indexStep, chainStep, diffStatePreActivationWrtInput?.entries)

            val stateSum = if (diffStatePreActivationWrtPreviousState != null) {

                doubleColumnVector(*add(diffStatePreActivationWrtPreviousState.entries, diffOutputPreActivationWrtState.entries))

            }
            else {
                diffOutputPreActivationWrtState

            }

            val (newDiffStatePreActivationWrtPreviousState, newDiffStatePreActivationWrtInput) = this.unit.backwardStep(indexStep, stateSum)

            diffStatePreActivationWrtInput = newDiffStatePreActivationWrtInput
            diffStatePreActivationWrtPreviousState = newDiffStatePreActivationWrtPreviousState

        }

        this.unit.backwardSeries()

        this.weighting.backwardSeries()
        this.bias?.backwardSeries()

        return diffStatePreActivationWrtPreviousState!!

    }

    private fun backwardOutput(indexStep: Int, chainStep: DoubleArray, diffStatePreActivationWrtInput: DoubleArray?): DoubleMatrix {

        val outputSum = doubleColumnVector(*

            // The input gradient for step t+1 is added to the chain step t ...
            if (diffStatePreActivationWrtInput != null) {

                // d chain / d output(index+1) * d output(index+1) / d input(index + 1) *  d input(index + 1) / d output(index)
                add(chainStep, diffStatePreActivationWrtInput)

            }
            // ... except in the case of the last step (t = T)
            else {

                chainStep

            })

        val diffOutputWrtOutputPreActivation = this.activations[indexStep].backward(outputSum)

        this.bias?.backwardStep(diffOutputWrtOutputPreActivation)

        val diffOutputPreActivationWrtState = this.weighting.backwardStep(indexStep, diffOutputWrtOutputPreActivation)

        return diffOutputPreActivationWrtState

    }

    override fun optimize(scalingFactor : Double) {

        if (this.unit is Optimizable) {

            this.unit.optimize(scalingFactor)

        }

        this.weighting.optimize(scalingFactor)
        this.bias?.optimize(scalingFactor)

    }

}

fun singleInputDecoder(
    numberSteps: Int,
    hiddenDimension : Int,
    outputDimension: Int,
    unit : RecurrentUnit,
    weightInitializationStrategy: InitializationStrategy,
    biasInitializationStrategy: InitializationStrategy?,
    activationFunction: ActivationFunction,
    optimizationStrategy: OptimizationStrategy?) =

    singleInputDecoder(
        null,
        numberSteps,
        hiddenDimension,
        outputDimension,
        unit,
        weightInitializationStrategy,
        biasInitializationStrategy,
        activationFunction,
        optimizationStrategy)


fun singleInputDecoder(
    name : String?,
    numberSteps: Int,
    hiddenDimension : Int,
    outputDimension: Int,
    unit : RecurrentUnit,
    weightInitializationStrategy: InitializationStrategy,
    biasInitializationStrategy: InitializationStrategy?,
    activationFunction: ActivationFunction,
    optimizationStrategy: OptimizationStrategy?): SingleInputDecoder {

    val weightingSeriesName = concatenateNames(name, "weighting")
    val weightingStepName = concatenateNames(name, "weighting-step")
    val weighting = seriesWeighting(weightingSeriesName, weightingStepName, numberSteps, false, hiddenDimension, outputDimension, weightInitializationStrategy, optimizationStrategy)

    val bias =
        if (biasInitializationStrategy != null) {

            val biasSeriesName = concatenateNames(name, "bias")
            seriesBias(biasSeriesName, outputDimension, biasInitializationStrategy, optimizationStrategy)

        }
        else {

            null

        }

    val activationName = concatenateNames(name, "activation")
    val activations = activationLayers(
        numberSteps,
        activationName,
        activationFunction
    )

    val decoder = SingleInputDecoder(
        name,
        numberSteps,
        outputDimension,
        unit,
        weighting,
        bias,
        activations)

    return decoder

}