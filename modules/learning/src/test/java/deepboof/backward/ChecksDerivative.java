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

package deepboof.backward;

import deepboof.Accuracy;
import deepboof.DFunction;
import deepboof.DeepUnitTest;
import deepboof.Tensor;
import deepboof.factory.FactoryBackwards;
import deepboof.forward.ChecksGenericFunction;
import deepboof.misc.TensorFactory;
import deepboof.misc.TensorOps_F64;
import deepboof.tensors.Tensor_F64;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * @author Peter Abeles
 */
public abstract class ChecksDerivative<T extends Tensor<T>>
	extends ChecksGenericFunction<T>
{

	protected FactoryBackwards<T> factoryD;

	protected Accuracy tolerance = Accuracy.RELAXED_A;

	public abstract DFunction<T> createBackwards(int type );

	@Before
	public void before() {
		tensorFactory = new TensorFactory<>(createBackwards(0).getTensorType());
		factoryD = new FactoryBackwards<>(createBackwards(0).getTensorType());
	}

		/**
	 * Tests the {@link DFunction#backwards}
	 */
	@Test
	public void checkBackwardsRandomInput() {

		NumericalGradient<T> numeric = factoryD.createNumericalGradient();

		for (int algConfig = 0; algConfig < numberOfConfigurations ; algConfig++) {
			DFunction<T> alg = createBackwards(algConfig);

			numeric.setFunction(alg);

			List<Case> testCases = createTestInputs();

			for (boolean sub : new boolean[]{false, true}) {
				for (Case testCase : testCases) {
					System.out.println("sub "+sub+"  input.length "+testCase.inputShape.length);
					T inputTensor = tensorFactory.randomM(random, sub, testCase.minibatch, testCase.inputShape);

					alg.initialize(testCase.inputShape);

					List<T> parameters = createParameters(alg, inputTensor);

					T outputTensor = tensorFactory.randomM(random, sub, testCase.minibatch, alg.getOutputShape());
					T dout = tensorFactory.randomM(random, sub, testCase.minibatch, alg.getOutputShape());

					// User the numerical gradient as ground truth for the gradient
					T expectedXD = tensorFactory.randomM(random, sub, testCase.minibatch, testCase.inputShape);
					List<T> expectedWD = createParameters(alg, inputTensor);
					numeric.differentiate(inputTensor,parameters,dout,expectedXD,expectedWD);

					// invoke the forwards pass first.  Some algorithms require it be called first
					alg.setParameters(parameters);
					alg.forward(inputTensor, outputTensor);

					// compute the gradient using the function being tested
					T foundXD = tensorFactory.randomM(random, sub, testCase.minibatch, testCase.inputShape);
					List<T> foundWD = createParameters(alg, inputTensor);
					alg.backwards(inputTensor,dout,foundXD,foundWD);

					TensorOps_F64.printSpatial((Tensor_F64)expectedXD,0,0);
					TensorOps_F64.printSpatial((Tensor_F64)foundXD,0,0);

					// compare results
					DeepUnitTest.assertEquals(expectedXD,foundXD, tolerance );
					for (int i = 0; i < expectedWD.size(); i++) {
						T e = expectedWD.get(i);
						T f = foundWD.get(i);
						DeepUnitTest.assertEquals(e, f, tolerance );
					}
				}
			}
		}
	}

	public abstract List<T> createParameters(DFunction<T> function , T input );
}
