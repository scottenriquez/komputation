package shape.komputation.loss

import shape.komputation.cpu.loss.CpuLogisticLoss
import shape.komputation.cuda.CudaContext
import shape.komputation.cuda.loss.CudaLogisticLoss

class LogisticLoss(private val numberCategories : Int, private val numberSteps : Int) : CpuLossFunctionInstruction, CudaLossFunctionInstruction {

    override fun buildForCpu() =

        CpuLogisticLoss()

    override fun buildForCuda(context: CudaContext): CudaLogisticLoss {

        val kernelFactory = context.kernelFactory

        val forwardKernel = { blockSize : Int -> kernelFactory.logisticLoss(blockSize) }
        val backwardKernel = { kernelFactory.backwardLogisticLoss() }

        return CudaLogisticLoss(
            this.numberCategories,
            this.numberSteps,
            forwardKernel,
            backwardKernel,
            context.numberMultiprocessors,
            context.maximumNumberOfResidentWarpsPerMultiprocessor,
            context.warpSize,
            context.maximumNumberOfThreadsPerBlock)

    }

}

fun logisticLoss(numberCategories: Int, numberSteps: Int = 1) =

    LogisticLoss(numberCategories, numberSteps)