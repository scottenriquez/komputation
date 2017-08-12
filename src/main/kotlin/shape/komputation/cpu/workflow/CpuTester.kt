package shape.komputation.cpu.workflow

import shape.komputation.cpu.Network
import shape.komputation.cpu.evaluation.computeAccuracy
import shape.komputation.cpu.functions.findMaxIndices
import shape.komputation.matrix.Matrix
import shape.komputation.matrix.partitionIndices

class CpuTester(
    private val network : Network,
    private val inputs : Array<Matrix>,
    private val targets: Array<FloatArray>,
    batchSize : Int,
    private val numberCategories : Int,
    private val length : Int) {

    private val numberInstances = inputs.size
    private val batches = partitionIndices(inputs.size, batchSize)

    private val actualCategories = Array(this.numberInstances) { IntArray(this.length) }
    private val predictedCategories = Array(this.numberInstances) { IntArray(this.length) }

    fun run(): Float {

        for((withinBatch, batch) in batches.withIndex()) {

            for (index in batch) {

                val input = this.inputs[index]

                val batchTargets = this.targets[index]
                findMaxIndices(batchTargets, this.numberCategories, this.length, this.actualCategories[index])

                val batchPredictions = this.network.forward(withinBatch, input, false)
                findMaxIndices(batchPredictions, this.numberCategories, this.length, this.predictedCategories[index])

            }

        }

        val accuracy = computeAccuracy(this.actualCategories, this.predictedCategories, this.numberInstances)

        return accuracy

    }

}