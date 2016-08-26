/*
 * Copyright (c) 2016, Peter Abeles. All Rights Reserved.
 *
 * This file is part of DeepBoof
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package deepboof.impl.backward.standard;

import deepboof.DeepBoofConstants;
import deepboof.backward.DFunctionBatchNorm;
import deepboof.misc.TensorOps;
import deepboof.tensors.Tensor_F64;

import java.util.List;

/**
 * Implementation of {@link DFunctionBatchNorm} for {@link Tensor_F64}.  In the forward pass BLAH is cached
 * for use in the backwards pass.
 *
 * @author Peter Abeles
 */
public class DFunctionBatchNorm_F64 extends BaseDFunction<Tensor_F64> implements DFunctionBatchNorm<Tensor_F64>
{
    // If gamma and beta are used as parameters
    protected boolean requiresGammaBeta;

    // storage for mean and standard deviation tensor
    protected Tensor_F64 tensorMean = new Tensor_F64();
    protected Tensor_F64 tensorStd = new Tensor_F64();  // really is sqrt( stdev^2 + eps) ~= stdev
    // storage for the normalized input  (e.g. stdev = 1, mean = 1)
    protected Tensor_F64 tensorXhat = new Tensor_F64();

    // Internal storage for gamma and beta parameters
    protected Tensor_F64 params = new Tensor_F64(0);
    protected double EPS = DeepBoofConstants.TEST_TOL_F64*0.1;

    public DFunctionBatchNorm_F64(boolean requiresGammaBeta) {
        this.requiresGammaBeta = requiresGammaBeta;
    }

    @Override
    public void _initialize() {
        tensorMean.reshape( shapeInput );
        tensorStd.reshape( shapeInput );

        this.shapeOutput = shapeInput.clone();

        if( requiresGammaBeta ) {
            int shapeParam[] = TensorOps.WI(shapeInput, 2);
            this.shapeParameters.add(shapeParam);
            params.reshape(shapeParam);
        }
    }

    @Override
    public void _setParameters(List<Tensor_F64> parameters) {
        if( requiresGammaBeta ) {
            params.setTo(parameters.get(0));
        } else if( parameters.size() != 0 ){
            throw new IllegalArgumentException("There are no parameters since gamma and beta have been turned off");
        }
    }

    @Override
    public void _forward(Tensor_F64 input, Tensor_F64 output) {
        if( input.length(0) <= 1 )
            throw new IllegalArgumentException("There must be more than 1 minibatch");

        if( requiresGammaBeta ) {
            tensorXhat.reshape( input.shape );
            computeStatisticsAndNormalize(input,tensorXhat);
            applyGammaBeta(input, output);
        } else {
            // is gamma and beta is off then the output is the normalized x_hat
            computeStatisticsAndNormalize(input,output);
        }
    }

    /**
     * Apply gamma and beta to normalized input x_hat
     */
    private void applyGammaBeta(Tensor_F64 input, Tensor_F64 output) {
        int indexOut = output.startIndex;
        int indexXHat = 0;
        int end = params.length();

        for (int stack = 0; stack < miniBatchSize; stack++) {
            int indexParam = params.startIndex;
            while (indexParam < end) {
                double gamma = params.d[indexParam++];
                double beta = params.d[indexParam++];

                output.d[indexOut++] = gamma*tensorXhat.d[indexXHat++] + beta;
            }
        }
    }

    /**
     * Computes and stores mean, standard deviation, and x_hat the normalized input vector
     */
    private void computeStatisticsAndNormalize(Tensor_F64 input, Tensor_F64 tensorXhat) {
        tensorMean.zero();
        tensorStd.zero();
        tensorXhat.zero();

        // length of the tensor outside of the batch norm
        int D = TensorOps.outerLength(input.shape,1);

        // compute the mean
        int indexIn = input.startIndex;
        for (int stack = 0; stack < miniBatchSize; stack++) {
            int indexMV = 0;
            while (indexMV < D) {
                tensorMean.d[indexMV++] += input.d[indexIn++];
            }
        }
        int indexMV = 0;
        while (indexMV < D) {
            tensorMean.d[indexMV++] /= miniBatchSize;
        }

        // compute the unbiased standard deviation with EPS for numerical reasons
        indexIn = input.startIndex;
        for (int stack = 0; stack < miniBatchSize; stack++) {
            indexMV = 0;
            while (indexMV < D) {
                double d = tensorMean.d[indexMV] - input.d[indexIn++];
                tensorStd.d[indexMV++] += d*d;
            }
        }
        indexMV = 0;
        while (indexMV < D) {
            tensorStd.d[indexMV] = Math.sqrt( tensorStd.d[indexMV]/(miniBatchSize-1) + EPS);
            indexMV++;
        }

        // normalize so that mean is 1 and variance is 1
        // x_hat = (x - mu)/std
        indexIn = input.startIndex;
        int indexXHat = tensorXhat.startIndex;
        for (int stack = 0; stack < miniBatchSize; stack++) {
            indexMV = 0;
            while (indexMV < D) {
                tensorXhat.d[indexXHat++] = (input.d[indexIn++] - tensorMean.d[indexMV]) / tensorStd.d[indexMV];
                indexMV++;
            }
        }
    }

    @Override
    protected void _backwards(Tensor_F64 input, Tensor_F64 dout, Tensor_F64 gradientInput, List<Tensor_F64> gradientParameters) {

    }

    @Override
    public double getEPS() {
        return EPS;
    }

    @Override
    public void setEPS(double EPS) {
        this.EPS = EPS;
    }

    @Override
    public boolean hasGammaBeta() {
        return requiresGammaBeta;
    }

    @Override
    public Class<Tensor_F64> getTensorType() {
        return Tensor_F64.class;
    }

    @Override
    public Tensor_F64 getMean( Tensor_F64 output ) {
        if( output == null )
            output = tensorMean.createLike();

        output.setTo(tensorMean);

        return output;
    }

    @Override
    public Tensor_F64 getVariance( Tensor_F64 output ) {
        if( output == null )
            output = tensorStd.createLike();

        output.reshape(tensorStd.getShape());

        int indexOut = output.startIndex;
        int indexStd = 0;

        int length = tensorStd.length();

        for (int i = 0; i < length; i++) {
            double d = tensorStd.d[indexStd++];
            output.d[indexOut++] = d*d - EPS;
        }

        return output;
    }
}