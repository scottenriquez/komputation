package com.komputation.optimization.adaptive

import com.komputation.cpu.optimization.adaptive.CpuRMSProp
import com.komputation.cuda.CudaContext
import com.komputation.cuda.kernels.OptimizationKernels
import com.komputation.cuda.optimization.adaptive.CudaRMSProp
import com.komputation.optimization.OptimizationInstruction

class RMSProp internal constructor(private val learningRate: Float, private val decay : Float, private val epsilon: Float) : OptimizationInstruction {

    override fun buildForCpu() =

        { numberRows : Int, numberColumns : Int ->

            CpuRMSProp(this.learningRate, this.decay, this.epsilon, numberRows * numberColumns)

        }

    override fun buildForCuda(context: CudaContext) =

        { numberParameters : Int, numberRows: Int, numberColumns: Int ->

            CudaRMSProp(
                numberParameters,
                numberRows * numberColumns,
                this.learningRate,
                this.decay,
                this.epsilon,
                { context.createKernel(OptimizationKernels.rmsprop()) },
                context.numberMultiprocessors,
                context.maximumNumberOfResidentWarpsPerMultiprocessor,
                context.warpSize,
                context.maximumNumberOfThreadsPerBlock)

        }

}

fun rmsprop(learningRate: Float, decay : Float = 0.9f, epsilon: Float = 1e-6f) =

    RMSProp(learningRate, decay, epsilon)