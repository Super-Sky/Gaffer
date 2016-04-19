/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gaffer.tuple.function;

import gaffer.function2.StatelessFunction;
import gaffer.tuple.Tuple;
import gaffer.tuple.function.context.FunctionContext;
import gaffer.tuple.function.context.FunctionContexts;

/**
 * A <code>TupleTransformer</code> transforms input {@link gaffer.tuple.Tuple}s by applying
 * {@link gaffer.function2.StatelessFunction}s to the tuple values.
 * @param <R> The type of reference used by tuples.
 */
public class TupleTransformer<R> extends TupleFunction<StatelessFunction, R> implements StatelessFunction<Tuple<R>, Tuple<R>> {
    /**
     * Default constructor - for serialisation.
     */
    public TupleTransformer() { }

    /**
     * Create a <code>TupleTransformer</code> that applies the given functions.
     * @param transforms {@link gaffer.function2.StatelessFunction}s to transform tuple values.
     */
    public TupleTransformer(final FunctionContexts<StatelessFunction, R> transforms) {
        setFunctions(transforms);
    }

    /**
     * Transform an input tuple.
     * @param input Input tuple.
     * @return Input tuple with transformed content.
     */
    @Override
    public Tuple<R> execute(final Tuple<R> input) {
        if (functions != null) {
            for (FunctionContext<StatelessFunction, R> transform : functions) {
                transform.project(input, transform.getFunction().execute(transform.select(input)));
            }
        }
        return input;
    }

    /**
     * @return New <code>TupleTransformer</code> with new {@link gaffer.function2.StatelessFunction}s.
     */
    public TupleTransformer<R> copy() {
        TupleTransformer<R> copy = new TupleTransformer<R>();
        if (this.functions != null) {
            copy.setFunctions(this.functions.copy());
        }
        return copy;
    }
}
