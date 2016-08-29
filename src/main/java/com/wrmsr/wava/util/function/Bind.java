/*
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
package com.wrmsr.wava.util.function;

public final class Bind
{
    private Bind()
    {
    }

    // TODO: @MethodHandle.PolymorphicSignature

    public static <T0, R> Function0<R> bind(Function1<T0, R> fn, T0 t0)
    {
        return () -> fn.apply(t0);
    }

    public static <T0, T1, R> Function1<T1, R> bind(Function2<T0, T1, R> fn, T0 t0)
    {
        return (t1) -> fn.apply(t0, t1);
    }

    public static <T0, T1, T2, R> Function2<T1, T2, R> bind(Function3<T0, T1, T2, R> fn, T0 t0)
    {
        return (t1, t2) -> fn.apply(t0, t1, t2);
    }

    public static <T0, T1, T2, T3, R> Function3<T1, T2, T3, R> bind(Function4<T0, T1, T2, T3, R> fn, T0 t0)
    {
        return (t1, t2, t3) -> fn.apply(t0, t1, t2, t3);
    }
}
