extern "C"
__global__ void stochasticGradientDescentKernel (
    int numberIterations,
    float learningRate,
    int* parameterIndices,
    int parameterSize,
    float* parameters,
    float scalingFactor,
    float* gradient)
{

    int startEntry = threadIdx.x * numberIterations;

    if(startEntry < parameterSize) {

        int indexParameter = parameterIndices[blockIdx.x];

        int startGradient = blockIdx.x * parameterSize + startEntry;
        int startParameter = indexParameter * parameterSize + startEntry;

        for(int i = 0; i < numberIterations; i++) {

            parameters[startParameter + i] -= scalingFactor * learningRate * gradient[startGradient + i];

        }

    }

}