/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kylin.metadata.filter;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;

import org.apache.kylin.metadata.filter.function.BuiltInMethod;
import org.apache.kylin.metadata.model.TblColRef;
import org.apache.kylin.metadata.tuple.IEvaluatableTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.primitives.Primitives;

public class BuiltInFunctionTupleFilter extends FunctionTupleFilter {
    public static final Logger logger = LoggerFactory.getLogger(BuiltInFunctionTupleFilter.class);

    protected String name;
    // FIXME Only supports single parameter functions currently
    protected TupleFilter columnContainerFilter;//might be a ColumnTupleFilter(simple case) or FunctionTupleFilter(complex case like substr(lower()))
    protected int colPosition;
    protected Method method;
    protected List<Serializable> methodParams;
    protected boolean isValidFunc = false;

    public BuiltInFunctionTupleFilter(String name) {
        super(Lists.<TupleFilter> newArrayList(), FilterOperatorEnum.FUNCTION);
        this.methodParams = Lists.newArrayList();

        if (name != null) {
            this.name = name.toUpperCase();
            initMethod();
        }
    }

    public String getName() {
        return name;
    }

    public TblColRef getColumn() {
        if (columnContainerFilter == null)
            return null;

        if (columnContainerFilter instanceof ColumnTupleFilter)
            return ((ColumnTupleFilter) columnContainerFilter).getColumn();
        else if (columnContainerFilter instanceof BuiltInFunctionTupleFilter)
            return ((BuiltInFunctionTupleFilter) columnContainerFilter).getColumn();

        throw new UnsupportedOperationException("Wrong type TupleFilter in FunctionTupleFilter.");
    }

    public Object invokeFunction(Object input) throws InvocationTargetException, IllegalAccessException {
        if (columnContainerFilter instanceof ColumnTupleFilter)
            methodParams.set(colPosition, (Serializable) input);
        else if (columnContainerFilter instanceof BuiltInFunctionTupleFilter)
            methodParams.set(colPosition, (Serializable) ((BuiltInFunctionTupleFilter) columnContainerFilter).invokeFunction(input));
        return method.invoke(null, (Object[]) (methodParams.toArray()));
    }

    public boolean isValid() {
        return isValidFunc && method != null && methodParams.size() == children.size();
    }

    @Override
    public void addChild(TupleFilter child) {
        if (child instanceof ColumnTupleFilter || child instanceof BuiltInFunctionTupleFilter) {
            columnContainerFilter = child;
            colPosition = methodParams.size();
            methodParams.add(null);
        } else if (child instanceof ConstantTupleFilter) {
            Serializable constVal = (Serializable) child.getValues().iterator().next();
            try {
                Class<?> clazz = Primitives.wrap(method.getParameterTypes()[methodParams.size()]);
                if (!Primitives.isWrapperType(clazz))
                    methodParams.add(constVal);
                else
                    methodParams.add((Serializable) clazz.cast(clazz.getDeclaredMethod("valueOf", String.class).invoke(null, constVal)));
            } catch (Exception e) {
                logger.warn(e.getMessage());
                isValidFunc = false;
            }
        }
        super.addChild(child);
    }

    @Override
    public boolean isEvaluable() {
        return false;
    }

    @Override
    public boolean evaluate(IEvaluatableTuple tuple, IFilterCodeSystem<?> cs) {
        throw new UnsupportedOperationException("Function filter cannot be evaluated immediately");
    }

    @Override
    public Collection<String> getValues() {
        throw new UnsupportedOperationException("Function filter cannot be evaluated immediately");
    }

    @Override
    public void serialize(IFilterCodeSystem<?> cs, ByteBuffer buffer) {
        throw new UnsupportedOperationException("Function filter cannot serialized");
    }

    @Override
    public void deserialize(IFilterCodeSystem<?> cs, ByteBuffer buffer) {
        throw new UnsupportedOperationException("Function filter cannot serialized");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append("(");
        for (int i = 0; i < methodParams.size(); i++) {
            if (colPosition == i) {
                sb.append(columnContainerFilter);
            } else {
                sb.append(methodParams.get(i));
            }
            if (i < methodParams.size() - 1)
                sb.append(",");
        }
        sb.append(")");
        return sb.toString();
    }

    protected void initMethod() {
        if (BuiltInMethod.MAP.containsKey(name)) {
            this.method = BuiltInMethod.MAP.get(name).method;
            isValidFunc = true;
        }
    }
}