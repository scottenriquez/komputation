package shape.komputation.cpu.layers.forward.projection

import shape.komputation.cpu.layers.CpuLayerState
import shape.komputation.cpu.optimization.DenseAccumulator
import shape.komputation.cpu.optimization.UpdateRule
import shape.komputation.cpu.optimization.updateDensely
import shape.komputation.initialization.InitializationStrategy
import shape.komputation.initialization.initializeColumnVector
import shape.komputation.layers.concatenateNames
import shape.komputation.optimization.OptimizationInstruction

class SeriesBias internal constructor(
    private val name : String?,
    private val inputDimension: Int,
    private val layers: Array<CpuBiasLayer>,
    private val bias: FloatArray,
    private val seriesAccumulator: DenseAccumulator,
    private val batchAccumulator: DenseAccumulator,
    private val updateRule: UpdateRule? = null) : CpuLayerState {

    private val numberBiasEntries = this.bias.size

    override val numberOutputRows = this.inputDimension
    override val numberOutputColumns = 1
    override var forwardResult = FloatArray(0)

    override val numberInputRows = this.inputDimension
    override val numberInputColumns = 1
    override var backwardResult = FloatArray(0)

    fun forwardStep(withinBatch : Int, step : Int, input : FloatArray, isTraining : Boolean): FloatArray {

        this.forwardResult = this.layers[step].forward(withinBatch, 1, input, isTraining)

        return this.forwardResult

    }

    fun backwardStep(withinBatch: Int, step: Int, chain: FloatArray): FloatArray {

        this.backwardResult = this.layers[step].backward(withinBatch, chain)

        return this.backwardResult

    }

    fun backwardSeries() {

        this.batchAccumulator.accumulate(this.seriesAccumulator.getAccumulation())
        this.seriesAccumulator.reset()

    }

    fun optimize(scalingFactor : Float) {

        if (this.updateRule != null) {

            updateDensely(this.bias, this.batchAccumulator.getAccumulation(), this.numberBiasEntries, scalingFactor, this.updateRule)

        }

        this.batchAccumulator.reset()

    }

}

fun seriesBias(
    numberSteps: Int,
    numberInputRows: Int,
    initialization: InitializationStrategy,
    optimization: OptimizationInstruction?) =

    seriesBias(null, null, numberSteps, numberInputRows, initialization, optimization)

fun seriesBias(
    seriesName: String?,
    stepNamePrefix: String?,
    numberSteps: Int,
    inputDimension: Int,
    initialization: InitializationStrategy,
    optimization: OptimizationInstruction?) : SeriesBias {

    val bias = initializeColumnVector(initialization, inputDimension)

    val seriesAccumulator = DenseAccumulator(inputDimension)

    val biasLayers = Array(numberSteps) { indexStep ->

        val stepName = concatenateNames(stepNamePrefix, indexStep.toString())

        CpuBiasLayer(stepName, inputDimension, 1, 1, bias, seriesAccumulator)

    }

    val batchAccumulator = DenseAccumulator(inputDimension)

    val optimizationStrategy = optimization?.buildForCpu()
    val updateRule = optimizationStrategy?.invoke(inputDimension, 1)

    return SeriesBias(
        seriesName,
        inputDimension,
        biasLayers,
        bias,
        seriesAccumulator,
        batchAccumulator,
        updateRule)

}