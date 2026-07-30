/********************************************************************************
 * Copyright (c) 2021, 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.jifa.hda.impl;

import org.eclipse.jifa.analysis.cache.Cacheable;
import org.eclipse.jifa.analysis.listener.ProgressListener;
import org.eclipse.jifa.common.domain.exception.CommonException;
import org.eclipse.jifa.common.domain.request.PagingRequest;
import org.eclipse.jifa.common.domain.vo.PageView;
import org.eclipse.jifa.common.util.PageViewBuilder;
import org.eclipse.jifa.hda.api.AnalysisException;
import org.eclipse.jifa.hda.api.HeapDumpAnalyzer;
import org.eclipse.jifa.hda.api.Model;
import org.eclipse.jifa.hda.api.SearchPredicate;
import org.eclipse.jifa.hda.api.SearchType;
import org.eclipse.jifa.hda.api.SortTableGenerator;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.hprof.extension.HprofPreferencesAccess;
import org.eclipse.mat.internal.snapshot.SnapshotQueryContext;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.XClassHistogramRecord;
import org.eclipse.mat.parser.model.XClassLoaderHistogramRecord;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultPie;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.query.refined.RefinedTree;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.HistogramRecord;
import org.eclipse.mat.snapshot.IPathsFromGCRootsComputer;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.snapshot.UnreachableObjectsHistogram;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.snapshot.query.SnapshotQuery;

import java.lang.ref.Cleaner;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.eclipse.jifa.analysis.listener.ProgressListener.NoOpProgressListener;
import static org.eclipse.jifa.common.Constant.EMPTY_STRING;
import static org.eclipse.jifa.hda.api.Model.CalciteSQLResult;
import static org.eclipse.jifa.hda.api.Model.ClassReferrer;
import static org.eclipse.jifa.hda.api.Model.Comparison;
import static org.eclipse.jifa.hda.api.Model.DirectByteBuffer;
import static org.eclipse.jifa.hda.api.Model.DominatorTree;
import static org.eclipse.jifa.hda.api.Model.DuplicatedClass;
import static org.eclipse.jifa.hda.api.Model.GCRoot;
import static org.eclipse.jifa.hda.api.Model.GCRootPath;
import static org.eclipse.jifa.hda.api.Model.InspectorView;
import static org.eclipse.jifa.hda.api.Model.JavaObject;
import static org.eclipse.jifa.hda.api.Model.LeakReport;
import static org.eclipse.jifa.hda.api.Model.OQLResult;
import static org.eclipse.jifa.hda.api.Model.Overview;
import static org.eclipse.jifa.hda.api.Model.TheString;
import static org.eclipse.jifa.hda.api.Model.UnreachableObject;
import static org.eclipse.jifa.hda.api.SearchPredicate.createPredicate;
import static org.eclipse.jifa.hda.impl.AnalysisContext.ClassLoaderExplorerData;
import static org.eclipse.jifa.hda.impl.AnalysisContext.DirectByteBufferData;

@SuppressWarnings("unchecked")
public class HeapDumpAnalyzerImpl implements HeapDumpAnalyzer {

    private static final Cleaner CLEANER = Cleaner.create();

    static final Provider PROVIDER = new ProviderImpl();

    private final AnalysisContext context;

    private final Cleaner.Cleanable cleaner;

    public HeapDumpAnalyzerImpl(AnalysisContext context) {
        this.context = context;
        this.cleaner = CLEANER.register(this, () -> $(() -> SnapshotFactory.dispose(context.snapshot)));
    }

    static {
        try {
            java.lang.reflect.Field field = SnapshotFactory.class.getDeclaredField("factory");
            field.setAccessible(true);
            Object factory = field.get(null);
            field = factory.getClass().getDeclaredField("snapshotCache");
            field.setAccessible(true);
            field.set(factory, new ConcurrentHashMap<>());
        } catch (Throwable t) {
            throw new AnalysisException(t);
        }
    }

    public static int typeOf(IObject object) {
        if (object instanceof IClass) {
            return JavaObject.CLASS_TYPE;
        }

        if (object instanceof IClassLoader) {
            return JavaObject.CLASS_LOADER_TYPE;
        }

        if (object.getClazz().isArrayType()) {
            return JavaObject.ARRAY_TYPE;
        }
        return JavaObject.NORMAL_TYPE;
    }

    private static int getClassReferrerType(URL icon) {
        if (icon == Icons.CLASS_IN || icon == Icons.CLASS_OUT) {
            return ClassReferrer.Type.NEW;
        } else if (icon == Icons.CLASS_IN_MIXED || icon == Icons.CLASS_OUT_MIXED) {
            return ClassReferrer.Type.MIXED;
        } else if (icon == Icons.CLASS_IN_OLD || icon == Icons.CLASS_OUT_OLD) {
            return ClassReferrer.Type.OLD_FAD;
        }
        throw new AnalysisException("Should not reach here");
    }

    private static Map<IClass, Set<String>> convert(AnalysisContext context,
                                                    List<String> excludes) throws SnapshotException {
        Map<IClass, Set<String>> excludeMap = null;

        if (excludes != null && !excludes.isEmpty()) {
            excludeMap = new HashMap<>();

            for (String entry : excludes) {
                String pattern = entry;
                Set<String> fields = null;
                int colon = entry.indexOf(':');

                if (colon >= 0) {
                    fields = new HashSet<>();

                    StringTokenizer tokens = new StringTokenizer(entry.substring(colon + 1), ",");
                    while (tokens.hasMoreTokens())
                        fields.add(tokens.nextToken());

                    pattern = pattern.substring(0, colon);
                }

                for (IClass clazz : context.snapshot.getClassesByName(Pattern.compile(pattern), true))
                    excludeMap.put(clazz, fields);
            }
        }

        return excludeMap;
    }

    private static <V> V $(RV<V> rv) {
        try {
            return rv.run();
        } catch (RuntimeException t) {
            throw t;
        } catch (Throwable t) {
            throw new AnalysisException(t);
        }
    }

    private static <V> V $(RV<V> rv, V def) {
        try {
            return rv.run();
        } catch (Throwable t) {
            return def;
        }
    }

    private static void $(R e) {
        $(() -> {
            e.run();
            return null;
        });
    }

    @Override
    public void dispose() {
        cleaner.clean();
    }

    @Override
    public Overview.Details getDetails() {
        return $(() -> {
                     SnapshotInfo snapshotInfo = context.snapshot.getSnapshotInfo();
                     return new Overview.Details(snapshotInfo.getIdentifierSize(),
                                                 snapshotInfo.getCreationDate().getTime(),
                                                 snapshotInfo.getNumberOfObjects(),
                                                 snapshotInfo.getNumberOfGCRoots(),
                                                 snapshotInfo.getNumberOfClasses(),
                                                 snapshotInfo.getNumberOfClassLoaders(),
                                                 snapshotInfo.getUsedHeapSize(),
                                                 getJVMOptions());
                 }
                );
    }

    private List<String> getJVMOptions() {
        return $(() -> {
            List<String> result = new ArrayList<>();
            IResult oqlResult = getOQLResult(context, "select v.vmArgs.list.a.@objectId from sun.management.VMManagementImpl v");
            if (oqlResult instanceof IResultTable t && t.getRowCount() == 1) {
                Object row = t.getRow(0);
                Integer id = (Integer) t.getColumnValue(row, 0);
                IObjectArray array = (IObjectArray) context.snapshot.getObject(id);
                long[] refs = array.getReferenceArray();
                for (long address : refs) {
                    if (address != 0) {
                        IObject object = context.snapshot.getObject(context.snapshot.mapAddressToId(address));
                        result.add(object.getClassSpecificName());
                    }
                }
            }
            return result;
        }, Collections.emptyList());
    }

    private <Res extends IResult> Res queryByCommand(AnalysisContext context,
                                                     String command) throws SnapshotException {
        return queryByCommand(context, command, null, NoOpProgressListener);
    }

    @Cacheable
    protected <Res extends IResult> Res queryByCommand(AnalysisContext context,
                                                       String command,
                                                       Map<String, Object> args) throws SnapshotException {
        return queryByCommand(context, command, args, NoOpProgressListener);
    }

    private <Res extends IResult> Res queryByCommand(AnalysisContext context, String command,
                                                     ProgressListener listener) throws SnapshotException {
        return queryByCommand(context, command, null, listener);
    }

    @SuppressWarnings("unchecked")
    private <Res extends IResult> Res queryByCommand(AnalysisContext context, String command,
                                                     Map<String, Object> args,
                                                     ProgressListener listener) throws SnapshotException {
        SnapshotQuery query = SnapshotQuery.parse(command, context.snapshot);
        if (args != null) {
            args.forEach((k, v) -> $(() -> query.setArgument(k, v)));
        }
        return (Res) query.execute(new ProgressListenerImpl(listener));
    }

    @Override
    public Map<String, String> getSystemProperties() {
        return $(() -> {
            IResultTable result = queryByCommand(context, "system_properties");
            Map<String, String> map = new HashMap<>();
            int count = result.getRowCount();
            for (int i = 0; i < count; i++) {
                Object row = result.getRow(i);
                map.put((String) result.getColumnValue(row, 1), (String) result.getColumnValue(row, 2));
            }
            return map;
        });
    }

    @Override
    public Map<String, String> getEnvVariables() {
        return $(() -> {
            Map<String, String> env = new HashMap<>();
            Collection<IClass> classes = context.snapshot.getClassesByName("java.lang.ProcessEnvironment", true);
            if(classes == null || classes.isEmpty()){
                return env;
            }
            IClass systemClass = classes.iterator().next();
            IObject iObject = (IObject) systemClass.resolveValue("theEnvironment");
            IResultTable result = (IResultTable) SnapshotQuery.lookup("hash_entries", context.snapshot)
                    .setArgument("objects", iObject).execute(new ProgressListenerImpl(NoOpProgressListener));
            int rowCount = result.getRowCount();
            for(int i = 0; i< rowCount; i++){
                Object row = result.getRow(i);
                env.put((String) result.getColumnValue(row, 1), (String) result.getColumnValue(row, 2));
            }
            return env;
        });
    }

    @Override
    public JavaObject getObjectInfo(int objectId) {
        return $(() -> {
            JavaObject ho = new JavaObject();
            IObject object = context.snapshot.getObject(objectId);
            ho.setObjectId(objectId);
            ho.setLabel(EscapeUtil.unescapeLabel(object.getDisplayName()));
            ho.setShallowSize(object.getUsedHeapSize());
            ho.setRetainedSize(object.getRetainedHeapSize());
            ho.setObjectType(typeOf(object));
            ho.setGCRoot(context.snapshot.isGCRoot(objectId));
            ho.setHasOutbound(true);
            ho.setSuffix(Helper.suffix(context.snapshot, objectId));
            return ho;
        });
    }

    @Override
    public InspectorView getInspectorView(int objectId) {
        return $(() -> {
            InspectorView view = new InspectorView();

            ISnapshot snapshot = context.snapshot;
            IObject object = snapshot.getObject(objectId);

            view.setObjectAddress(object.getObjectAddress());
            IClass iClass = object instanceof IClass ? (IClass) object : object.getClazz();
            view.setName(iClass.getName());
            view.setObjectType(typeOf(object));
            view.setGCRoot(snapshot.isGCRoot(objectId));

            // class name and address of the object
            IClass clazz = object.getClazz();
            view.setClassLabel(clazz.getTechnicalName());
            view.setClassGCRoot(clazz.getGCRootInfo() != null);

            // super class name
            if (iClass.getSuperClass() != null) {
                view.setSuperClassName(iClass.getSuperClass().getName());
            }

            // class loader name and address
            IObject classLoader = snapshot.getObject(iClass.getClassLoaderId());
            view.setClassLoaderLabel(classLoader.getTechnicalName());
            view.setClassLoaderGCRoot(classLoader.getGCRootInfo() != null);

            view.setShallowSize(object.getUsedHeapSize());
            view.setRetainedSize(object.getRetainedHeapSize());
            // gc root
            GCRootInfo[] gcRootInfo = object.getGCRootInfo();
            view.setGcRootInfo(
                    gcRootInfo != null ? "GC root: " + GCRootInfo.getTypeSetAsString(object.getGCRootInfo())
                            : "no GC root");
            return view;
        });
    }

    private String getObjectValue(IObject o) {
        String text = o.getClassSpecificName();
        return text != null ? EscapeUtil.unescapeJava(text) : o.getTechnicalName();
    }

    private PageView<Model.FieldView> buildPageViewOfFields(List<Field> fields, int page, int pageSize) {
        return PageViewBuilder.build(fields, new PagingRequest(page, pageSize), field -> {
            Model.FieldView fv = new Model.FieldView();
            fv.fieldType = field.getType();
            fv.name = field.getName();
            Object value = field.getValue();
            if (value instanceof ObjectReference) {
                try {
                    fv.objectId = ((ObjectReference) value).getObjectId();
                    fv.value = getObjectValue(((ObjectReference) value).getObject());
                } catch (SnapshotException e) {
                    throw new AnalysisException(e);
                }
            } else if (value != null) {
                fv.value = value.toString();
            }
            return fv;
        });
    }

    @Override
    public PageView<Model.FieldView> getFields(int objectId, int page, int pageSize) {
        return $(() -> {
            ISnapshot snapshot = context.snapshot;
            IObject object = snapshot.getObject(objectId);

            PagingRequest pagingRequest = new PagingRequest(page, pageSize);
            if (object instanceof IPrimitiveArray) {
                List<Model.FieldView> fvs = new ArrayList<>();
                IPrimitiveArray pa = (IPrimitiveArray) object;
                int firstIndex = (pagingRequest.getPage() - 1) * pagingRequest.getPageSize();
                int lastIndex = Math.min(firstIndex + pagingRequest.getPageSize(), pa.getLength());
                for (int i = firstIndex; i < lastIndex; i++) {
                    fvs.add(new Model.FieldView(pa.getType(), "[" + i + "]", pa.getValueAt(i).toString()));
                }
                return new PageView<>(pagingRequest, pa.getLength(), fvs);
            } else if (object instanceof IObjectArray) {
                List<Model.FieldView> fvs = new ArrayList<>();
                IObjectArray oa = (IObjectArray) object;
                int firstIndex = (pagingRequest.getPage() - 1) * pagingRequest.getPageSize();
                int lastIndex = Math.min(firstIndex + pagingRequest.getPageSize(), oa.getLength());
                for (int i = firstIndex; i < lastIndex; i++) {
                    long[] refs = oa.getReferenceArray(i, 1);
                    int refObjectId = 0;
                    if (refs[0] != 0) {
                        refObjectId = snapshot.mapAddressToId(refs[0]);
                    }
                    String value = null;
                    if (refObjectId != 0) {
                        value = getObjectValue(snapshot.getObject(refObjectId));
                    }
                    fvs.add(new Model.FieldView(IObject.Type.OBJECT, "[" + i + "]", value, refObjectId));
                }
                return new PageView<>(pagingRequest, oa.getLength(), fvs);
            }

            List<Field> fields = new ArrayList<>();
            boolean isClass = object instanceof IClass;
            IClass clazz = isClass ? (IClass) object : object.getClazz();
            if (object instanceof IInstance) {
                fields.addAll(((IInstance) object).getFields());
            } else if (object instanceof IClass) {
                do {
                    List<Field> staticFields = clazz.getStaticFields();
                    for (Field staticField : staticFields) {
                        if (staticField.getName().startsWith("<")) {
                            fields.add(staticField);
                        }
                    }
                } while ((clazz = clazz.getSuperClass()) != null);

            }
            return buildPageViewOfFields(fields, page, pageSize);

        });
    }

    @Override
    public PageView<Model.FieldView> getStaticFields(int objectId, int page,
                                                     int pageSize) {
        return $(() -> {
            ISnapshot snapshot = context.snapshot;
            IObject object = snapshot.getObject(objectId);
            boolean isClass = object instanceof IClass;
            IClass clazz = isClass ? (IClass) object : object.getClazz();

            List<Field> fields = new ArrayList<>();
            do {
                List<Field> staticFields = clazz.getStaticFields();
                for (Field staticField : staticFields) {
                    if (!staticField.getName().startsWith("<")) {
                        fields.add(staticField);
                    }
                }
            } while (!isClass && (clazz = clazz.getSuperClass()) != null);
            return buildPageViewOfFields(fields, page, pageSize);
        });
    }

    @Override
    public int mapAddressToId(long address) {
        return $(() -> context.snapshot.mapAddressToId(address));
    }

    @Override
    public String getObjectValue(int objectId) {
        return $(() -> {
            IObject object = context.snapshot.getObject(objectId);
            String text = object.getClassSpecificName();
            return text != null ? EscapeUtil.unescapeJava(text) : EMPTY_STRING;
        });
    }

    @Override
    public List<Overview.BigObject> getBiggestObjects() {
        return $(() -> {
            IResultPie result = queryByCommand(context, "pie_biggest_objects");

            List<? extends IResultPie.Slice> slices = result.getSlices();
            return slices
                    .stream()
                    .map(slice -> new Overview.BigObject(slice.getLabel(), slice.getContext() != null
                            ? slice.getContext().getObjectId() :
                            Helper.ILLEGAL_OBJECT_ID,
                                                         slice.getValue(), slice.getDescription()))
                    .collect(Collectors.toList());
        });
    }

    private ClassLoaderExplorerData queryClassLoader(AnalysisContext context) throws Exception {
        ClassLoaderExplorerData classLoaderExplorerData = context.classLoaderExplorerData.get();
        if (classLoaderExplorerData != null) {
            return classLoaderExplorerData;
        }
        // The context monitor now guards only the short queries (this one and
        // queryDirectByteBufferData); the BFS-backed caches in AnalysisContext have their own
        // locks or need none, so they no longer block whoever locks on the context.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (context) {
            classLoaderExplorerData = context.classLoaderExplorerData.get();
            if (classLoaderExplorerData != null) {
                return classLoaderExplorerData;
            }
            IResultTree result = queryByCommand(context, "ClassLoaderExplorerQuery");
            classLoaderExplorerData = new ClassLoaderExplorerData();
            classLoaderExplorerData.result = result;

            Map<Integer, Object> classLoaderIdMap = new HashMap<>();
            for (Object r : result.getElements()) {
                classLoaderIdMap.put(result.getContext(r).getObjectId(), r);
            }
            classLoaderExplorerData.classLoaderIdMap = classLoaderIdMap;

            classLoaderExplorerData.items = result.getElements();
            classLoaderExplorerData.items.sort((Comparator<Object>) (o1, o2) -> Integer
                    .compare((int) result.getColumnValue(o2, 1), (int) result.getColumnValue(o1, 1)));

            for (Object item : classLoaderExplorerData.items) {
                classLoaderExplorerData.definedClasses += (int) result.getColumnValue(item, 1);
                classLoaderExplorerData.numberOfInstances += (int) result.getColumnValue(item, 2);
            }
            context.classLoaderExplorerData = new SoftReference<>(classLoaderExplorerData);
            return classLoaderExplorerData;
        }
    }

    @Override
    public Model.ClassLoader.Summary getSummaryOfClassLoaders() {
        return $(() -> {
            ClassLoaderExplorerData data = queryClassLoader(context);
            Model.ClassLoader.Summary summary =
                    new Model.ClassLoader.Summary();
            summary.setTotalSize(data.items.size());
            summary.setDefinedClasses(data.definedClasses);
            summary.setNumberOfInstances(data.numberOfInstances);
            return summary;
        });
    }

    @Override
    public PageView<Model.ClassLoader.Item> getClassLoaders(int page, int pageSize) {
        return $(() -> {
            ClassLoaderExplorerData data = queryClassLoader(context);
            IResultTree result = data.result;
            return PageViewBuilder.build(data.items, new PagingRequest(page, pageSize), e -> {
                Model.ClassLoader.Item r = new Model.ClassLoader.Item();
                r.setObjectId(result.getContext(e).getObjectId());
                r.setPrefix(((IDecorator) result).prefix(e));
                r.setLabel((String) result.getColumnValue(e, 0));
                r.setDefinedClasses((Integer) result.getColumnValue(e, 1));
                r.setNumberOfInstances((Integer) result.getColumnValue(e, 2));
                r.setClassLoader(true);
                // FIXME
                r.setHasParent(false);
                return r;
            });
        });
    }

    @Override
    public PageView<Model.ClassLoader.Item> getChildrenOfClassLoader(int classLoaderId, int page, int pageSize) {
        return $(() -> {
            ClassLoaderExplorerData data = queryClassLoader(context);
            IResultTree result = data.result;
            Object o = data.classLoaderIdMap.get(classLoaderId);
            List<?> children = result.getChildren(o);
            return PageViewBuilder.build(children, new PagingRequest(page, pageSize), e -> {
                Model.ClassLoader.Item r = new Model.ClassLoader.Item();
                r.setObjectId(result.getContext(e).getObjectId());
                r.setPrefix(((IDecorator) result).prefix(e));
                r.setLabel((String) result.getColumnValue(e, 0));
                r.setNumberOfInstances((Integer) result.getColumnValue(e, 2));
                if (!(e instanceof IClass)) {
                    r.setClassLoader(true);
                    r.setDefinedClasses((Integer) result.getColumnValue(e, 1));
                    // FIXME
                    r.setHasParent(false);
                }
                return r;
            });
        });
    }

    @Override
    public UnreachableObject.Summary getSummaryOfUnreachableObjects() {
        return $(() -> {
            UnreachableObjectsHistogram histogram =
                    (UnreachableObjectsHistogram) context.snapshot.getSnapshotInfo().getProperty(
                            UnreachableObjectsHistogram.class.getName());
            UnreachableObject.Summary summary =
                    new UnreachableObject.Summary();
            if (histogram != null) {
                summary.setTotalSize(histogram.getRowCount());
                int objects = 0;
                long shallowSize = 0;
                for (Object record : histogram.getRecords()) {
                    objects += (Integer) histogram.getColumnValue(record, 1);
                    shallowSize += ((Bytes) histogram.getColumnValue(record, 2)).getValue();
                }

                summary.setObjects(objects);
                summary.setShallowSize(shallowSize);
            }
            return summary;
        });
    }

    @Override
    public PageView<UnreachableObject.Item> getUnreachableObjects(int page, int pageSize) {
        return $(() -> {
            UnreachableObjectsHistogram histogram =
                    (UnreachableObjectsHistogram) context.snapshot.getSnapshotInfo().getProperty(
                            UnreachableObjectsHistogram.class.getName());

            List<?> total = new ArrayList<>(histogram.getRecords());
            total.sort((Comparator<Object>) (o1, o2) -> {
                long v2 = ((Bytes) histogram.getColumnValue(o2, 2)).getValue();
                long v1 = ((Bytes) histogram.getColumnValue(o1, 2)).getValue();
                return Long.compare(v2, v1);
            });

            return PageViewBuilder.build(total, new PagingRequest(page, pageSize), record -> {
                UnreachableObject.Item r = new UnreachableObject.Item();
                r.setClassName((String) histogram.getColumnValue(record, 0));
                r.setObjectId(Helper.fetchObjectId(histogram.getContext(record)));
                r.setObjects((Integer) histogram.getColumnValue(record, 1));
                r.setShallowSize(((Bytes) histogram.getColumnValue(record, 2)).getValue());
                return r;
            });
        });
    }

    private DirectByteBufferData queryDirectByteBufferData(
            AnalysisContext context) throws SnapshotException {
        DirectByteBufferData data = context.directByteBufferData.get();
        if (data != null) {
            return data;
        }

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (context) {
            data = context.directByteBufferData.get();
            if (data != null) {
                return data;
            }

            data = new DirectByteBufferData();
            IResult result = queryByCommand(context, "oql", DirectByteBufferData.ARGS);
            IResultTable table;
            if (result instanceof IResultTable) {
                table = (IResultTable) result;

                RefinedResultBuilder builder =
                        new RefinedResultBuilder(new SnapshotQueryContext(context.snapshot), table);
                builder.setSortOrder(3, Column.SortDirection.DESC);
                data.resultContext = (RefinedTable) builder.build();
                DirectByteBuffer.Summary summary = new DirectByteBuffer.Summary();
                summary.totalSize = data.resultContext.getRowCount();

                for (int i = 0; i < summary.totalSize; i++) {
                    Object row = data.resultContext.getRow(i);
                    summary.position += data.position(row);
                    summary.limit += data.limit(row);
                    summary.capacity += data.capacity(row);
                }
                data.summary = summary;
            } else {
                data.summary = new DirectByteBuffer.Summary();
            }
            context.directByteBufferData = new SoftReference<>(data);
            return data;
        }
    }

    @Override
    public DirectByteBuffer.Summary getSummaryOfDirectByteBuffers() {
        return $(() -> queryDirectByteBufferData(context).summary);
    }

    @Override
    public PageView<DirectByteBuffer.Item> getDirectByteBuffers(int page, int pageSize) {
        return $(() -> {
            DirectByteBufferData data = queryDirectByteBufferData(context);
            RefinedTable resultContext = data.resultContext;
            return PageViewBuilder.build(new PageViewBuilder.Callback<Object>() {
                @Override
                public int totalSize() {
                    return data.summary.totalSize;
                }

                @Override
                public Object get(int index) {
                    return resultContext.getRow(index);
                }
            }, new PagingRequest(page, pageSize), row -> {
                DirectByteBuffer.Item item = new DirectByteBuffer.Item();
                item.objectId = resultContext.getContext(row).getObjectId();
                item.label = data.label(row);
                item.position = data.position(row);
                item.limit = data.limit(row);
                item.capacity = data.capacity(row);
                return item;
            });
        });
    }

    private PageView<JavaObject> queryIOBoundsOfObject(AnalysisContext context, int objectId, int page,
                                                       int pageSize, boolean outbound) throws SnapshotException {
        ISnapshot snapshot = context.snapshot;
        int[] ids = outbound ? snapshot.getOutboundReferentIds(objectId) : snapshot.getInboundRefererIds(objectId);

        return PageViewBuilder.build(ids, new PagingRequest(page, pageSize), id -> {
            try {
                JavaObject o = new JavaObject();
                IObject object = context.snapshot.getObject(id);
                o.setObjectId(id);
                o.setLabel(object.getDisplayName());
                o.setShallowSize(object.getUsedHeapSize());
                o.setRetainedSize(object.getRetainedHeapSize());
                o.setObjectType(typeOf(object));
                o.setGCRoot(snapshot.isGCRoot(id));
                o.setHasOutbound(true);
                o.setHasInbound(true);
                o.setPrefix(Helper.prefix(snapshot, outbound ? objectId : id, outbound ? id : objectId));
                o.setSuffix(Helper.suffix(snapshot, id));
                return o;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public PageView<JavaObject> getOutboundOfObject(int objectId, int page, int pageSize) {
        return $(() -> queryIOBoundsOfObject(context, objectId, page, pageSize, true));
    }

    @Override
    public PageView<JavaObject> getInboundOfObject(int objectId, int page, int pageSize) {
        return $(() -> queryIOBoundsOfObject(context, objectId, page, pageSize, false));
    }

    @Override
    public List<GCRoot.Item> getGCRoots() {
        return $(() -> {
            IResultTree tree = queryByCommand(context, "gc_roots");
            return tree.getElements().stream().map(e -> {
                GCRoot.Item item = new GCRoot.Item();
                item.setClassName((String) tree.getColumnValue(e, 0));
                item.setObjects((Integer) tree.getColumnValue(e, 1));
                return item;
            }).collect(Collectors.toList());
        });
    }

    @Override
    public PageView<TheString.Item> getStrings(String pattern, int page, int pageSize) {
        return $(() -> {
            // Let MAT do the matching: find_strings applies the pattern to the string value
            // (IObject#getClassSpecificName), which is not what the result's first column exposes
            // (that is the display name, i.e. "java.lang.String @ 0x... <value truncated at 256>").
            // Filtering on the display name in Java would both match on class name and address and
            // miss content beyond the truncation point.
            String matPattern = pattern == null || pattern.isEmpty() ? ".*" : ".*" + pattern + ".*";
            // Cache per pattern so that paging through one result set scans the heap only once.
            // No locking: the query is idempotent, so a cold-start race at worst repeats the scan,
            // whereas a lock held across a full-heap scan would stall every other search.
            SoftReference<AnalysisContext.StringData> ref = context.stringsCache.get(matPattern);
            AnalysisContext.StringData cachedData = ref != null ? ref.get() : null;
            if (cachedData == null) {
                // Pass the pattern as an argument instead of appending "-pattern <p>" to the
                // command: the command string is tokenized on whitespace, so a spliced pattern
                // containing a space would be parsed as several arguments and rejected.
                Map<String, Object> args = new HashMap<>();
                args.put("pattern", Pattern.compile(matPattern));
                IResultTree tree = queryByCommand(context, "find_strings java.lang.String", args);
                cachedData = new AnalysisContext.StringData();
                cachedData.tree = tree;
                cachedData.nodes = tree.getElements();
                // Drop entries whose result has been reclaimed so the map does not keep growing.
                context.stringsCache.values().removeIf(value -> value.get() == null);
                context.stringsCache.put(matPattern, new SoftReference<>(cachedData));
            }

            IResultTree tree = cachedData.tree;
            return PageViewBuilder.build(cachedData.nodes, new PagingRequest(page, pageSize), node -> {
                TheString.Item item = new TheString.Item();
                int id = tree.getContext(node).getObjectId();
                item.setObjectId(id);
                item.setLabel((String) tree.getColumnValue(node, 0));
                item.setShallowSize(((Bytes) tree.getColumnValue(node, 1)).getValue());
                item.setRetainedSize(((Bytes) tree.getColumnValue(node, 2)).getValue());
                return item;
            });
        });
    }

    @Override
    public PageView<GCRoot.Item> getClassesOfGCRoot(int rootTypeIndex, int page, int pageSize) {
        return $(() -> {
            IResultTree tree = queryByCommand(context, "gc_roots");
            Object root = tree.getElements().get(rootTypeIndex);
            List<?> classes = tree.getChildren(root);
            return PageViewBuilder.build(classes, new PagingRequest(page, pageSize), clazz -> {
                GCRoot.Item item = new GCRoot.Item();
                item.setClassName((String) tree.getColumnValue(clazz, 0));
                item.setObjects((Integer) tree.getColumnValue(clazz, 1));
                item.setObjectId(tree.getContext(clazz).getObjectId());
                return item;
            });
        });
    }

    @Override
    public PageView<JavaObject> getObjectsOfGCRoot(int rootTypeIndex, int classIndex, int page, int pageSize) {
        return $(() -> {
            IResultTree tree = queryByCommand(context, "gc_roots");
            Object root = tree.getElements().get(rootTypeIndex);
            List<?> classes = tree.getChildren(root);
            Object clazz = classes.get(classIndex);
            List<?> objects = tree.getChildren(clazz);

            return PageViewBuilder.build(objects, new PagingRequest(page, pageSize),
                                         o -> $(() -> {
                                                    JavaObject ho = new JavaObject();
                                                    int objectId = tree.getContext(o).getObjectId();
                                                    IObject object = context.snapshot.getObject(objectId);
                                                    ho.setLabel(object.getDisplayName());
                                                    ho.setObjectId(objectId);
                                                    ho.setShallowSize(object.getUsedHeapSize());
                                                    ho.setRetainedSize(object.getRetainedHeapSize());
                                                    ho.setObjectType(typeOf(object));
                                                    ho.setGCRoot(context.snapshot.isGCRoot(objectId));
                                                    ho.setSuffix(Helper.suffix(object.getGCRootInfo()));
                                                    ho.setHasOutbound(true);
                                                    ho.setHasInbound(true);
                                                    return ho;
                                                }
                                               ));
        });
    }

    private IResultTree queryIOBoundClassOfClassReference(AnalysisContext context, Object idOrIds,
                                                          boolean outbound) throws SnapshotException {
        Map<String, Object> args = new HashMap<>();
        if (idOrIds instanceof int[]) {
            args.put("objects", idOrIds);
        } else {
            args.put("objects", new int[]{(Integer) idOrIds});
        }
        args.put("inbound", !outbound);
        return queryByCommand(context, "class_references", args);
    }

    private ClassReferrer.Item buildClassReferenceItem(IResultTree result, Object row) {
        ClassReferrer.Item item = new ClassReferrer.Item();
        item.label = (String) result.getColumnValue(row, 0);
        item.objects = (Integer) result.getColumnValue(row, 1);
        item.shallowSize = ((Bytes) result.getColumnValue(row, 2)).getValue();
        IContextObjectSet context = (IContextObjectSet) result.getContext(row);
        item.objectId = context.getObjectId();
        item.objectIds = context.getObjectIds();
        item.setType(getClassReferrerType(((IIconProvider) result).getIcon(row)));
        return item;
    }

    @Override
    public ClassReferrer.Item getOutboundClassOfClassReference(int objectId) {
        return $(() -> {
            IResultTree result = queryIOBoundClassOfClassReference(context, objectId, true);
            return buildClassReferenceItem(result, result.getElements().get(0));
        });
    }

    @Override
    public ClassReferrer.Item getInboundClassOfClassReference(int objectId) {
        return $(() -> {
            IResultTree result = queryIOBoundClassOfClassReference(context, objectId, false);
            return buildClassReferenceItem(result, result.getElements().get(0));
        });
    }

    @Override
    public PageView<ClassReferrer.Item> getOutboundsOfClassReference(int[] objectId, int page, int pageSize) {
        return $(() -> {
            IResultTree result = queryIOBoundClassOfClassReference(context, objectId, true);
            return PageViewBuilder
                    .build(result.getChildren(result.getElements().get(0)), new PagingRequest(page, pageSize),
                           e -> buildClassReferenceItem(result, e));
        });
    }

    @Override
    public PageView<ClassReferrer.Item> getInboundsOfClassReference(int[] objectId, int page, int pageSize) {
        return $(() -> {
            IResultTree result = queryIOBoundClassOfClassReference(context, objectId, false);
            return PageViewBuilder
                    .build(result.getChildren(result.getElements().get(0)), new PagingRequest(page, pageSize),
                           e -> buildClassReferenceItem(result, e));
        });
    }

    @Override
    public Comparison.Summary getSummaryOfComparison(Path other) {
        return $(() -> {
            ISnapshot baselineSnapshot = ((HeapDumpAnalyzerImpl) PROVIDER.provide(other, Collections.emptyMap(),
                                                                                  NoOpProgressListener)).context.snapshot;
            ISnapshot targetSnapshot = context.snapshot;
            Histogram targetHistogram = targetSnapshot.getHistogram(new ProgressListenerImpl(NoOpProgressListener));
            Histogram baselineHistogram = baselineSnapshot.getHistogram(new ProgressListenerImpl(NoOpProgressListener));
            final Histogram delta = targetHistogram.diffWithBaseline(baselineHistogram);

            long totalObjects = 0;
            long totalShallowHeap = 0;
            for (Object r : delta.getClassHistogramRecords()) {
                totalObjects += (long) delta.getColumnValue(r, 1);
                totalShallowHeap += ((Bytes) delta.getColumnValue(r, 2)).getValue();
            }

            Comparison.Summary summary = new Comparison.Summary();
            summary.setTotalSize(delta.getClassHistogramRecords().size());
            summary.setObjects(totalObjects);
            summary.setShallowSize(totalShallowHeap);
            return summary;
        });
    }

    @Override
    public PageView<Comparison.Item> getItemsOfComparison(Path other, int page, int pageSize) {
        return $(() -> {
            ISnapshot baselineSnapshot = ((HeapDumpAnalyzerImpl) PROVIDER.provide(other, Collections.emptyMap(),
                                                                                  NoOpProgressListener)).context.snapshot;
            ISnapshot targetSnapshot = context.snapshot;
            Histogram targetHistogram = targetSnapshot.getHistogram(new ProgressListenerImpl(NoOpProgressListener));
            Histogram baselineHistogram = baselineSnapshot.getHistogram(new ProgressListenerImpl(NoOpProgressListener));
            final Histogram delta = targetHistogram.diffWithBaseline(baselineHistogram);

            //noinspection
            ((List<ClassHistogramRecord>) delta.getClassHistogramRecords()).sort((o1, o2) -> Long
                    .compare(((Bytes) delta.getColumnValue(o2, 2)).getValue(),
                             ((Bytes) delta.getColumnValue(o1, 2)).getValue()));

            return PageViewBuilder.build(delta.getClassHistogramRecords(), new PagingRequest(page, pageSize), r -> {
                Comparison.Item record = new Comparison.Item();
                record.setClassName((String) delta.getColumnValue(r, 0));
                record.setObjects((Long) delta.getColumnValue(r, 1));
                record.setShallowSize(((Bytes) delta.getColumnValue(r, 2)).getValue());
                return record;
            });
        });
    }

    private IResultTree queryMultiplePath2GCRootsTreeByClassId(AnalysisContext context, int classId,
                                                               GCRootPath.Grouping grouping)
            throws Exception {
        ClassImpl clazz = (ClassImpl) context.snapshot.getObject(classId);
        return queryMultiplePath2GCRootsTreeByObjectIds(context, clazz.getObjectIds(), grouping);
    }

    private IResultTree queryMultiplePath2GCRootsTreeByObjectIds(AnalysisContext context, int[] objectIds,
                                                                 GCRootPath.Grouping grouping)
            throws Exception {
        if (grouping != GCRootPath.Grouping.FROM_GC_ROOTS) {
            throw new CommonException("Unsupported grouping now");
        }

        // Cache the BFS result by objectIds to avoid redundant O(N) graph traversals. The grouping
        // is not part of the key because only FROM_GC_ROOTS reaches this point (guarded above).
        AnalysisContext.MergePathTreeCacheKey cacheKey =
                new AnalysisContext.MergePathTreeCacheKey(objectIds);
        SoftReference<IResultTree> ref = context.mergePathTreeCache.get(cacheKey);
        IResultTree cached = ref != null ? ref.get() : null;
        if (cached != null) {
            return cached;
        }
        synchronized (context.mergePathTreeLock) {
            ref = context.mergePathTreeCache.get(cacheKey);
            cached = ref != null ? ref.get() : null;
            if (cached != null) {
                return cached;
            }
            // Entries whose tree has been reclaimed still pin their key, which holds one int per
            // requested object. Drop them here so the map does not keep growing across a session.
            context.mergePathTreeCache.values().removeIf(value -> value.get() == null);
            Map<String, Object> args = new HashMap<>();
            args.put("objects", Helper.buildHeapObjectArgument(objectIds));
            IResultTree tree = queryByCommand(context, "merge_shortest_paths", args);
            context.mergePathTreeCache.put(cacheKey, new SoftReference<>(tree));
            return tree;
        }
    }

    private PageView<GCRootPath.MergePathToGCRootsTreeNode> buildMergePathRootsNode(AnalysisContext context,
                                                                                    IResultTree tree, List<?> elements,
                                                                                    int page, int pageSize) {

        return PageViewBuilder.build(elements, new PagingRequest(page, pageSize), element -> $(() -> {
            ISnapshot snapshot = context.snapshot;
            GCRootPath.MergePathToGCRootsTreeNode record = new GCRootPath.MergePathToGCRootsTreeNode();
            int objectId = tree.getContext(element).getObjectId();
            IObject object = snapshot.getObject(objectId);
            record.setObjectId(tree.getContext(element).getObjectId());
            record.setObjectType(typeOf(object));
            record.setGCRoot(snapshot.isGCRoot(objectId));
            record.setClassName(tree.getColumnValue(element, 0).toString());
            record.setSuffix(Helper.suffix(object.getGCRootInfo()));
            record.setRefObjects((int) tree.getColumnValue(element, 1));
            record.setShallowHeap(((Bytes) tree.getColumnValue(element, 2)).getValue());
            record.setRefShallowHeap(((Bytes) tree.getColumnValue(element, 3)).getValue());
            record.setRetainedHeap(((Bytes) tree.getColumnValue(element, 4)).getValue());
            return record;
        }));
    }

    @Override
    public PageView<GCRootPath.MergePathToGCRootsTreeNode> getRootsOfMergePathToGCRootsByClassId(
            int classId, GCRootPath.Grouping grouping, int page, int pageSize) {
        return $(() -> {
            IResultTree tree = queryMultiplePath2GCRootsTreeByClassId(context, classId, grouping);
            return buildMergePathRootsNode(context, tree, tree.getElements(), page, pageSize);
        });
    }

    @Override
    public PageView<GCRootPath.MergePathToGCRootsTreeNode> getRootsOfMergePathToGCRootsByObjectIds(
            int[] objectIds, GCRootPath.Grouping grouping, int page, int pageSize) {
        return $(() -> {
            IResultTree tree = queryMultiplePath2GCRootsTreeByObjectIds(context, objectIds, grouping);
            return buildMergePathRootsNode(context, tree, tree.getElements(), page, pageSize);
        });
    }

    @Override
    public PageView<GCRootPath.MergePathToGCRootsTreeNode> getChildrenOfMergePathToGCRootsByClassId(
            int classId, int[] objectIdPathInGCPathTree, GCRootPath.Grouping grouping,
            int page, int pageSize) {
        return $(() -> {
            IResultTree tree = queryMultiplePath2GCRootsTreeByClassId(context, classId, grouping);
            Object object = Helper.fetchObjectInResultTree(tree, objectIdPathInGCPathTree);
            List<?> elements = object == null ? Collections.emptyList() : tree.getChildren(object);
            return buildMergePathRootsNode(context, tree, elements, page, pageSize);
        });
    }

    @Override
    public PageView<GCRootPath.MergePathToGCRootsTreeNode> getChildrenOfMergePathToGCRootsByObjectIds(
            int[] objectIds, int[] objectIdPathInGCPathTree, GCRootPath.Grouping grouping,
            int page, int pageSize) {
        return $(() -> {
            IResultTree tree = queryMultiplePath2GCRootsTreeByObjectIds(context, objectIds, grouping);
            Object object = Helper.fetchObjectInResultTree(tree, objectIdPathInGCPathTree);
            List<?> elements = object == null ? Collections.emptyList() : tree.getChildren(object);
            return buildMergePathRootsNode(context, tree, elements, page, pageSize);
        });
    }

    @Override
    public GCRootPath.Item getPathToGCRoots(int objectId, int skip, int count) {
        return $(() -> {
            ISnapshot snapshot = context.snapshot;
            Map<IClass, Set<String>> excludeMap = convert(context, GCRootPath.EXCLUDES);
            IPathsFromGCRootsComputer computer = snapshot.getPathsFromGCRoots(objectId, excludeMap);
            List<int[]> paths = new ArrayList<>();
            int index = 0;
            int[] current;
            int get = 0;
            while (get < count && (current = computer.getNextShortestPath()) != null) {
                if (index < skip) {
                    index++;
                    continue;
                }
                paths.add(current);
                get++;
            }

            boolean hasMore = computer.getNextShortestPath() != null;
            GCRootPath.Item item = new GCRootPath.Item();
            item.setCount(paths.size());
            item.setHasMore(hasMore);
            GCRootPath.Node origin = new GCRootPath.Node();
            IObject object = snapshot.getObject(objectId);
            origin.setOrigin(true);
            origin.setObjectId(objectId);
            origin.setLabel(object.getDisplayName());
            origin.setSuffix(Helper.suffix(snapshot, objectId));
            origin.setGCRoot(snapshot.isGCRoot(objectId));
            origin.setObjectType(typeOf(object));
            origin.setShallowSize(object.getUsedHeapSize());
            origin.setRetainedSize(object.getRetainedHeapSize());
            item.setTree(origin);

            if (paths.size() == 0) {
                return item;
            }

            for (int[] path : paths) {
                GCRootPath.Node parentNode = origin;
                for (index = 1; index < path.length; index++) {
                    int childId = path[index];
                    GCRootPath.Node childNode = parentNode.getChild(childId);
                    if (childNode == null) {
                        IObject childObj = snapshot.getObject(childId);
                        childNode = new GCRootPath.Node();
                        childNode.setObjectId(childId);
                        childNode.setPrefix(Helper.prefix(snapshot, childId, parentNode.getObjectId()));
                        childNode.setLabel(childObj.getDisplayName());
                        childNode.setSuffix(Helper.suffix(snapshot, childId));
                        childNode.setGCRoot(snapshot.isGCRoot(childId));
                        childNode.setObjectType(typeOf(childObj));
                        childNode.setShallowSize(childObj.getUsedHeapSize());
                        childNode.setRetainedSize(childObj.getRetainedHeapSize());
                        parentNode.addChild(childNode);
                    }
                    parentNode = childNode;
                }
            }
            return item;
        });
    }

    private LeakReport.ShortestPath buildPath(ISnapshot snapshot, RefinedTree rst,
                                              Object row) throws SnapshotException {
        LeakReport.ShortestPath shortestPath = new LeakReport.ShortestPath();
        shortestPath.setLabel((String) rst.getColumnValue(row, 0));
        shortestPath.setShallowSize(((Bytes) rst.getColumnValue(row, 1)).getValue());
        shortestPath.setRetainedSize(((Bytes) rst.getColumnValue(row, 2)).getValue());
        int objectId = rst.getContext(row).getObjectId();
        shortestPath.setObjectId(objectId);
        IObject object = snapshot.getObject(objectId);
        shortestPath.setGCRoot(snapshot.isGCRoot(objectId));
        shortestPath.setObjectType(typeOf(object));

        if (rst.hasChildren(row)) {
            List<LeakReport.ShortestPath> children = new ArrayList<>();
            shortestPath.setChildren(children);
            for (Object c : rst.getChildren(row)) {
                children.add(buildPath(snapshot, rst, c));
            }
        }
        return shortestPath;
    }

    @Override
    public LeakReport getLeakReport() {
        return $(() -> {
            // Check final LeakReport cache first (avoids BFS + object rebuild)
            LeakReport cached = context.leakReportCache.get();
            if (cached != null) {
                return cached;
            }
            synchronized (context.leakReportLock) {
                cached = context.leakReportCache.get();
                if (cached != null) {
                    return cached;
                }

                AnalysisContext.LeakReportData data = context.leakReportData.get();
                if (data == null) {
                    IResult result = queryByCommand(context, "leakhunter");
                    data = new AnalysisContext.LeakReportData();
                    data.result = result;
                    context.leakReportData = new SoftReference<>(data);
                }
                IResult result = data.result;
                LeakReport report = new LeakReport();
                if (result instanceof TextResult) {
                    report.setInfo(((TextResult) result).getText());
                } else if (result instanceof SectionSpec) {
                    report.setUseful(true);
                    SectionSpec sectionSpec = (SectionSpec) result;
                    report.setName(sectionSpec.getName());
                    List<Spec> specs = sectionSpec.getChildren();
                    for (int i = 0; i < specs.size(); i++) {
                        QuerySpec spec = (QuerySpec) specs.get(i);
                        String name = spec.getName();
                        if (name == null || name.isEmpty()) {
                            continue;
                        }
                        // LeakHunterQuery_Overview
                        if (name.startsWith("Overview")) {
                            IResultPie irtPie = (IResultPie) spec.getResult();
                            List<? extends IResultPie.Slice> pieSlices = irtPie.getSlices();

                            List<LeakReport.Slice> slices = new ArrayList<>();
                            for (IResultPie.Slice slice : pieSlices) {
                                slices.add(
                                        new LeakReport.Slice(slice.getLabel(),
                                                             Helper.fetchObjectId(slice.getContext()),
                                                             slice.getValue(), slice.getDescription()));
                            }
                            report.setSlices(slices);
                        }
                        // LeakHunterQuery_ProblemSuspect
                        // LeakHunterQuery_Hint
                        else if (name.startsWith("Problem Suspect") || name.startsWith("Hint")) {
                            LeakReport.Record suspect = new LeakReport.Record();
                            suspect.setIndex(i);
                            suspect.setName(name);
                            CompositeResult cr = (CompositeResult) spec.getResult();
                            List<CompositeResult.Entry> entries = cr.getResultEntries();
                            for (CompositeResult.Entry entry : entries) {
                                String entryName = entry.getName();
                                if (entryName == null || entryName.isEmpty()) {
                                    IResult r = entry.getResult();
                                    if (r instanceof QuerySpec &&
                                        // LeakHunterQuery_ShortestPaths
                                        ((QuerySpec) r).getName().equals("Shortest Paths To the Accumulation Point")) {
                                        IResultTree tree = (IResultTree) ((QuerySpec) r).getResult();
                                        RefinedResultBuilder builder = new RefinedResultBuilder(
                                                new SnapshotQueryContext(context.snapshot), tree);
                                        RefinedTree rst = (RefinedTree) builder.build();
                                        List<?> elements = rst.getElements();
                                        List<LeakReport.ShortestPath> paths = new ArrayList<>();
                                        suspect.setPaths(paths);
                                        for (Object row : elements) {
                                            paths.add(buildPath(context.snapshot, rst, row));
                                        }
                                    }
                                }
                                // LeakHunterQuery_Description
                                // LeakHunterQuery_Overview
                                else if ((entryName.startsWith("Description") || entryName.startsWith("Overview"))) {
                                    TextResult desText = (TextResult) entry.getResult();
                                    suspect.setDesc(desText.getText());
                                }
                            }
                            List<LeakReport.Record> records = report.getRecords();
                            if (records == null) {
                                report.setRecords(records = new ArrayList<>());
                            }
                            records.add(suspect);
                        }
                    }
                }

                context.leakReportCache = new SoftReference<>(report);
                return report;
            }
        });
    }

    @Cacheable
    protected IResult getOQLResult(AnalysisContext context, String oql) {
        return $(() -> {
            Map<String, Object> args = new HashMap<>();
            args.put("queryString", oql);
            return queryByCommand(context, "oql", args);
        });
    }

    @Override
    public CalciteSQLResult getCalciteSQLResult(String sql, String sortBy, boolean ascendingOrder, int page, int pageSize) {
        return $(() -> {
            Map<String, Object> args = new HashMap<>();
            args.put("sql", sql);
            IResult result;
            try {
                result = queryByCommand(context, "calcite", args);
            } catch (Throwable t) {
                return new CalciteSQLResult.TextResult(t.getMessage());
            }
            if (result instanceof IResultTree) {
                return new CalciteSQLResult.TreeResult(
                        PageViewBuilder.build(
                                ((IResultTree) result).getElements(),
                                new PagingRequest(page, pageSize),
                                e -> $(() -> context.snapshot.getObject(((IResultTree) result).getContext(e).getObjectId())),
                                o -> $(() -> {
                                    JavaObject jo = new JavaObject();
                                    jo.setObjectId(o.getObjectId());
                                    jo.setLabel(o.getDisplayName());
                                    jo.setSuffix(Helper.suffix(o.getGCRootInfo()));
                                    jo.setShallowSize(o.getUsedHeapSize());
                                    jo.setRetainedSize(o.getRetainedHeapSize());
                                    jo.setGCRoot(context.snapshot.isGCRoot(o.getObjectId()));
                                    jo.setObjectType(typeOf(o));
                                    jo.setHasOutbound(true);
                                    return jo;
                                }), IObjectSortHelper.sortBy(sortBy, ascendingOrder)));
            } else if (result instanceof IResultTable) {
                IResultTable table = (IResultTable) result;
                Column[] columns = table.getColumns();
                List<String> cs = Arrays.stream(columns).map(Column::getLabel).collect(Collectors.toList());
                PageView<CalciteSQLResult.TableResult.Entry> pv =
                        PageViewBuilder.build(new PageViewBuilder.Callback<Object>() {
                            @Override
                            public int totalSize() {
                                return table.getRowCount();
                            }

                            @Override
                            public Object get(int index) {
                                return table.getRow(index);
                            }
                        }, new PagingRequest(page, pageSize), o -> {
                            List<Object> l = new ArrayList<>();
                            for (int i = 0; i < columns.length; i++) {
                                Object columnValue = table.getColumnValue(o, i);

                                l.add(columnValue != null ? EscapeUtil.unescapeJava(columnValue.toString()) : null);
                            }
                            IContextObject co = table.getContext(o);
                            return new CalciteSQLResult.TableResult.Entry(co != null ? co.getObjectId() : Helper.ILLEGAL_OBJECT_ID,
                                                                          l);
                        });
                return new CalciteSQLResult.TableResult(cs, pv);
            } else if (result instanceof TextResult) {
                return new CalciteSQLResult.TextResult(((TextResult) result).getText());
            }
            return new CalciteSQLResult.TextResult("Unsupported Calcite SQL result type");
        });
    }

    static class IObjectSortHelper {

        static Map<String, Comparator<IObject>> sortTable = new SortTableGenerator<IObject>()
                .add("id", IObject::getObjectId)
                .add("shallowHeap", IObject::getUsedHeapSize)
                .add("retainedHeap", IObject::getRetainedHeapSize)
                .add("label", IObject::getDisplayName)
                .build();

        public static Comparator<IObject> sortBy(String field, boolean ascendingOrder) {
            return ascendingOrder ? sortTable.get(field) : sortTable.get(field).reversed();
        }
    }

    public OQLResult getOQLResult(String oql, String sortBy, boolean ascendingOrder, int page, int pageSize) {
        IResult result = getOQLResult(context, oql);
        return $(() -> {
            if (result instanceof IResultTree) {
                return new OQLResult.TreeResult(
                        PageViewBuilder.build(
                                ((IResultTree) result).getElements(),
                                new PagingRequest(page, pageSize),
                                e -> $(() -> context.snapshot.getObject(((IResultTree) result).getContext(e).getObjectId())),
                                o -> $(() -> {
                                    JavaObject jo = new JavaObject();
                                    jo.setObjectId(o.getObjectId());
                                    jo.setLabel(o.getDisplayName());
                                    jo.setSuffix(Helper.suffix(o.getGCRootInfo()));
                                    jo.setShallowSize(o.getUsedHeapSize());
                                    jo.setRetainedSize(o.getRetainedHeapSize());
                                    jo.setGCRoot(context.snapshot.isGCRoot(o.getObjectId()));
                                    jo.setObjectType(typeOf(o));
                                    jo.setHasOutbound(true);
                                    return jo;
                                }), IObjectSortHelper.sortBy(sortBy, ascendingOrder)));
            } else if (result instanceof IResultTable) {
                IResultTable table = (IResultTable) result;
                Column[] columns = table.getColumns();
                List<String> cs = Arrays.stream(columns).map(Column::getLabel).collect(Collectors.toList());
                PageView<OQLResult.TableResult.Entry> pv =
                        PageViewBuilder.build(new PageViewBuilder.Callback<Object>() {
                            @Override
                            public int totalSize() {
                                return table.getRowCount();
                            }

                            @Override
                            public Object get(int index) {
                                return table.getRow(index);
                            }
                        }, new PagingRequest(page, pageSize), o -> {
                            List<Object> l = new ArrayList<>();
                            for (int i = 0; i < columns.length; i++) {
                                Object columnValue = table.getColumnValue(o, i);

                                l.add(columnValue != null ? columnValue.toString() : null);
                            }
                            IContextObject co = table.getContext(o);
                            return new OQLResult.TableResult.Entry(co != null ? co.getObjectId() : Helper.ILLEGAL_OBJECT_ID,
                                                                   l);
                        });
                return new OQLResult.TableResult(cs, pv);
            } else if (result instanceof TextResult) {
                return new OQLResult.TextResult(((TextResult) result).getText());
            } else {
                throw new AnalysisException("Unsupported OQL result type");
            }
        });
    }

    @Override
    public Model.Thread.Summary getSummaryOfThreads(String searchText,
                                                    SearchType searchType) {
        return $(() -> {
            IResultTree result = queryByCommand(context, "thread_overview");
            List<Model.Thread.Item> items = result.getElements().stream()
                                                  .map(row -> new VirtualThreadItem(result, row))
                                                  .filter(SearchPredicate.createPredicate(searchText, searchType))
                                                  .collect(Collectors.toList());
            Model.Thread.Summary summary = new Model.Thread.Summary();
            summary.totalSize = items.size();
            summary.shallowHeap = items.stream().mapToLong(Model.Thread.Item::getShallowSize).sum();
            summary.retainedHeap = items.stream().mapToLong(Model.Thread.Item::getRetainedSize).sum();
            ;
            return summary;
        });
    }

    @Override
    public PageView<Model.Thread.Item> getThreads(String sortBy, boolean ascendingOrder, String searchText,
                                                  SearchType searchType, int page, int pageSize) {
        PagingRequest pagingRequest = new PagingRequest(page, pageSize);
        return $(() -> {
            IResultTree result = queryByCommand(context, "thread_overview");
            final AtomicInteger afterFilterCount = new AtomicInteger(0);
            List<Model.Thread.Item> items = result.getElements().stream()
                                                  .map(row -> new VirtualThreadItem(result, row))
                                                  .filter(SearchPredicate.createPredicate(searchText, searchType))
                                                  .peek(filtered -> afterFilterCount.incrementAndGet())
                                                  .sorted(Model.Thread.Item.sortBy(sortBy, ascendingOrder))
                                                  .skip(pagingRequest.from())
                                                  .limit(pagingRequest.getPageSize())
                                                  .collect(Collectors.toList());
            return new PageView<>(pagingRequest, afterFilterCount.get(), items);
        });
    }

    @Override
    public List<Model.Thread.StackFrame> getStackTrace(int objectId) {
        return $(() -> {
            Map<String, Object> args = new HashMap<>();
            args.put("objects", Helper.buildHeapObjectArgument(new int[]{objectId}));
            IResultTree result = queryByCommand(context, "thread_overview", args);

            List<?> elements = result.getElements();

            boolean includesMaxLocalRetained = (result.getColumns().length == 10);

            if (result.hasChildren(elements.get(0))) {

                List<?> frames = result.getChildren(elements.get(0));

                List<Model.Thread.StackFrame> res = frames.stream().map(
                        frame -> new Model.Thread.StackFrame(((String) result.getColumnValue(frame, 0)),
                                                             result.hasChildren(frame),
                                                             (includesMaxLocalRetained && result.getColumnValue(frame, 4) != null)
                                                                     ? ((Bytes) result.getColumnValue(frame, 4)).getValue()
                                                                     : 0L
                        )).collect(Collectors.toList());
                res.stream().filter(t -> !t.getStack().contains("Native Method")).findFirst()
                   .ifPresent(sf -> sf.setFirstNonNativeFrame(true));
                return res;
            }
            return Collections.emptyList();
        });
    }

    @Override
    public List<Model.Thread.LocalVariable> getLocalVariables(int objectId, int depth, boolean firstNonNativeFrame) {
        return $(() -> {
            Map<String, Object> args = new HashMap<>();
            args.put("objects", Helper.buildHeapObjectArgument(new int[]{objectId}));
            IResultTree result = queryByCommand(context, "thread_overview", args);

            List<?> elements = result.getElements();
            if (result.hasChildren(elements.get(0))) {
                List<?> frames = result.getChildren(elements.get(0));
                Object frame = frames.get(depth - 1);
                if (result.hasChildren(frame)) {
                    List<?> locals = result.getChildren(frame);
                    return locals.stream().map(local -> {
                        int id = result.getContext(local).getObjectId();
                        Model.Thread.LocalVariable var = new Model.Thread.LocalVariable();
                        var.setObjectId(id);
                        try {
                            IObject object = context.snapshot.getObject(id);
                            var.setLabel(object.getDisplayName());
                            var.setShallowSize(object.getUsedHeapSize());
                            var.setRetainedSize(object.getRetainedHeapSize());
                            var.setObjectType(typeOf(object));
                            var.setGCRoot(context.snapshot.isGCRoot(id));
                            var.setHasOutbound(result.hasChildren(var));
                            // ThreadStackQuery_Label_Local
                            var.setPrefix("<local>");
                            if (firstNonNativeFrame) {
                                GCRootInfo[] gcRootInfos = object.getGCRootInfo();
                                if (gcRootInfos != null) {
                                    for (GCRootInfo gcRootInfo : gcRootInfos) {
                                        if (gcRootInfo.getContextId() != 0 &&
                                            (gcRootInfo.getType() & GCRootInfo.Type.BUSY_MONITOR) != 0 &&
                                            gcRootInfo.getContextId() == objectId) {
                                            // ThreadStackQuery_Label_Local_Blocked_On
                                            var.setPrefix("<local, blocked on>");
                                        }
                                    }
                                }
                            }
                            var.setSuffix(Helper.suffix(context.snapshot, id));
                            return var;
                        } catch (SnapshotException e) {
                            throw new CommonException(e);
                        }
                    }).collect(Collectors.toList());
                }
            }
            return Collections.emptyList();
        });
    }

    @Override
    public PageView<DuplicatedClass.ClassItem> getDuplicatedClasses(String searchText,
                                                                    SearchType searchType, int page, int pageSize) {
        return $(() -> {
            IResultTree result = queryByCommand(context, "duplicate_classes");
            List<?> classes = result.getElements();
            classes.sort((o1, o2) -> ((List<?>) o2).size() - ((List<?>) o1).size());
            PageViewBuilder<?, DuplicatedClass.ClassItem> builder = PageViewBuilder.fromList(classes);
            AtomicInteger index = new AtomicInteger(0);
            return builder.paging(new PagingRequest(page, pageSize))
                          .map(r -> {
                              DuplicatedClass.ClassItem item = new DuplicatedClass.ClassItem();
                              item.setLabel((String) result.getColumnValue(r, 0));
                              item.setCount((Integer) result.getColumnValue(r, 1));
                              item.setIndex(index.getAndIncrement());
                              return item;
                          })
                          .filter(SearchPredicate.createPredicate(searchText, searchType))
                          .done();
        });
    }

    @Override
    public PageView<DuplicatedClass.ClassLoaderItem> getClassloadersOfDuplicatedClass(int index, int page,
                                                                                      int pageSize) {
        return $(() -> {
            IResultTree result = queryByCommand(context, "duplicate_classes");
            List<?> classes = result.getElements();
            classes.sort((o1, o2) -> ((List<?>) o2).size() - ((List<?>) o1).size());
            List<?> classLoaders = (List<?>) classes.get(index);
            return PageViewBuilder.build(classLoaders, new PagingRequest(page, pageSize), r -> {
                DuplicatedClass.ClassLoaderItem item = new DuplicatedClass.ClassLoaderItem();
                item.setLabel((String) result.getColumnValue(r, 0));
                item.setDefinedClassesCount((Integer) result.getColumnValue(r, 2));
                item.setInstantiatedObjectsCount((Integer) result.getColumnValue(r, 3));
                GCRootInfo[] roots;
                try {
                    roots = ((IClass) r).getGCRootInfo();
                } catch (SnapshotException e) {
                    throw new CommonException(e);
                }
                int id = ((IClass) r).getClassLoaderId();
                item.setObjectId(id);
                item.setGCRoot(context.snapshot.isGCRoot(id));
                item.setSuffix(roots != null ? GCRootInfo.getTypeSetAsString(roots) : null);
                return item;
            });
        });
    }

    @Override
    public PageView<Model.Histogram.Item> getHistogram(Model.Histogram.Grouping groupingBy,
                                                       int[] ids, String sortBy, boolean ascendingOrder,
                                                       String searchText, SearchType searchType,
                                                       int page, int pageSize) {
        return $(() -> {
            Map<String, Object> args = new HashMap<>();
            if (ids != null) {
                args.put("objects", Helper.buildHeapObjectArgument(ids));
            }
            IResult result = queryByCommand(context, "histogram -groupBy " + groupingBy.name(), args);
            switch (groupingBy) {
                case BY_CLASS:
                    Histogram h = (Histogram) result;
                    List<ClassHistogramRecord> records =
                            (List<ClassHistogramRecord>) h.getClassHistogramRecords();
                    return PageViewBuilder.<ClassHistogramRecord, Model.Histogram.Item>fromList(records)
                                          .beforeMap(record -> $(() -> record
                                                  .calculateRetainedSize(context.snapshot, true, true, Helper.VOID_LISTENER)))
                                          .paging(new PagingRequest(page, pageSize))
                                          .map(record -> new Model.Histogram.Item(record.getClassId(), record.getLabel(),
                                                                                  Model.Histogram.ItemType.CLASS,
                                                                                  record.getNumberOfObjects(),
                                                                                  record.getUsedHeapSize(),
                                                                                  record.getRetainedHeapSize()))
                                          .sort(Model.Histogram.Item.sortBy(sortBy, ascendingOrder))
                                          .filter(createPredicate(searchText, searchType))
                                          .done();
                case BY_CLASSLOADER:
                    Histogram.ClassLoaderTree ct = (Histogram.ClassLoaderTree) result;
                    @SuppressWarnings("unchecked")
                    PageViewBuilder<? extends XClassLoaderHistogramRecord, Model.Histogram.Item> builder =
                            PageViewBuilder.fromList((List<? extends XClassLoaderHistogramRecord>) ct.getElements());
                    return builder
                            .beforeMap(record -> $(() -> record.calculateRetainedSize(context.snapshot, true, true,
                                                                                      Helper.VOID_LISTENER)))
                            .paging(new PagingRequest(page, pageSize))
                            .map(record ->
                                         new Model.Histogram.Item(record.getClassLoaderId(), record.getLabel(),
                                                                  Model.Histogram.ItemType.CLASS_LOADER,
                                                                  record.getNumberOfObjects(),
                                                                  record.getUsedHeapSize(),
                                                                  record.getRetainedHeapSize())
                                )
                            .sort(Model.Histogram.Item.sortBy(sortBy, ascendingOrder))
                            .filter(createPredicate(searchText, searchType))
                            .done();
                case BY_SUPERCLASS:
                    Histogram.SuperclassTree st = (Histogram.SuperclassTree) result;
                    return PageViewBuilder.<HistogramRecord, Model.Histogram.Item>fromList(
                                                  (List<HistogramRecord>) st.getElements())
                                          .paging(new PagingRequest(page, pageSize))
                                          .map(e -> {
                                              Model.Histogram.Item item = new Model.Histogram.Item();
                                              int objectId = st.getContext(e).getObjectId();
                                              item.setType(Model.Histogram.ItemType.SUPER_CLASS);
                                              item.setObjectId(objectId);
                                              item.setLabel((String) st.getColumnValue(e, 0));
                                              item.setNumberOfObjects((Long) st.getColumnValue(e, 1));
                                              item.setShallowSize(((Bytes) st.getColumnValue(e, 2)).getValue());
                                              return item;
                                          })
                                          .sort(Model.Histogram.Item.sortBy(sortBy, ascendingOrder))
                                          .filter(createPredicate(searchText, searchType))
                                          .done();
                case BY_PACKAGE:
                    Histogram.PackageTree pt = (Histogram.PackageTree) result;
                    return
                            PageViewBuilder.<HistogramRecord, Model.Histogram.Item>fromList(
                                                   (List<HistogramRecord>) pt.getElements())
                                           .paging(new PagingRequest(page, pageSize))
                                           .map(e -> {

                                               Model.Histogram.Item item = new Model.Histogram.Item();
                                               String label = (String) pt.getColumnValue(e, 0);
                                               item.setLabel(label);

                                               if (e instanceof XClassHistogramRecord) {
                                                   int objectId = pt.getContext(e).getObjectId();
                                                   item.setObjectId(objectId);
                                                   item.setType(Model.Histogram.ItemType.CLASS);
                                               } else {
                                                   item.setObjectId(label.hashCode());
                                                   item.setType(Model.Histogram.ItemType.PACKAGE);
                                               }

                                               if (label.matches("^int(\\[\\])*") || label.matches("^char(\\[\\])*") ||
                                                   label.matches("^byte(\\[\\])*") || label.matches("^short(\\[\\])*") ||
                                                   label.matches("^boolean(\\[\\])*") ||
                                                   label.matches("^double(\\[\\])*") ||
                                                   label.matches("^float(\\[\\])*") || label.matches("^long(\\[\\])*") ||
                                                   label.matches("^void(\\[\\])*")) {
                                                   item.setType(Model.Histogram.ItemType.CLASS);
                                               }
                                               item.setNumberOfObjects((Long) pt.getColumnValue(e, 1));
                                               item.setShallowSize(((Bytes) pt.getColumnValue(e, 2)).getValue());

                                               return item;
                                           })
                                           .sort(Model.Histogram.Item.sortBy(sortBy, ascendingOrder))
                                           .filter(createPredicate(searchText, searchType))
                                           .done();
                default:
                    throw new AnalysisException("Should not reach here");
            }

        });
    }

    @Override
    public PageView<JavaObject> getHistogramObjects(int classId, int page, int pageSize) {
        return $(() -> {
            IResult result = queryByCommand(context, "histogram -groupBy BY_CLASS", Collections.emptyMap());
            Histogram h = (Histogram) result;
            List<ClassHistogramRecord> records =
                    (List<ClassHistogramRecord>) h.getClassHistogramRecords();

            Optional<ClassHistogramRecord> ro = records.stream().filter(r -> r.getClassId() == classId).findFirst();
            if (ro.isPresent()) {
                IContextObject c = ((Histogram) result).getContext(ro.get());
                if (c instanceof IContextObjectSet) {
                    int[] objectIds = ((IContextObjectSet) c).getObjectIds();
                    return PageViewBuilder.build(objectIds, new PagingRequest(page, pageSize), this::getObjectInfo);
                }
            }
            return PageView.empty();
        });
    }

    @Override
    public PageView<Model.Histogram.Item> getChildrenOfHistogram(Model.Histogram.Grouping groupBy, int[] ids,
                                                                 String sortBy, boolean ascendingOrder,
                                                                 int parentObjectId, int page, int pageSize) {
        return $(() -> {
            Map<String, Object> args = new HashMap<>();
            if (ids != null) {
                args.put("objects", Helper.buildHeapObjectArgument(ids));
            }
            IResult result = queryByCommand(context, "histogram -groupBy " + groupBy.name(), args);
            switch (groupBy) {
                case BY_CLASS: {
                    throw new AnalysisException("Should not reach here");
                }
                case BY_CLASSLOADER: {
                    Histogram.ClassLoaderTree tree = (Histogram.ClassLoaderTree) result;
                    List<?> elems = tree.getElements();
                    List<? extends ClassHistogramRecord> children = null;
                    for (Object elem : elems) {
                        if (elem instanceof XClassLoaderHistogramRecord) {
                            if (((XClassLoaderHistogramRecord) elem).getClassLoaderId() == parentObjectId) {
                                children = (List<? extends ClassHistogramRecord>) ((XClassLoaderHistogramRecord) elem)
                                        .getClassHistogramRecords();
                                break;
                            }
                        }
                    }
                    if (children != null) {
                        return PageViewBuilder.<ClassHistogramRecord, Model.Histogram.Item>fromList(
                                                      (List<ClassHistogramRecord>) children)
                                              .beforeMap(record -> $(() -> record
                                                      .calculateRetainedSize(context.snapshot, true, true, Helper.VOID_LISTENER)))
                                              .paging(new PagingRequest(page, pageSize))
                                              .map(record -> new Model.Histogram.Item(record.getClassId(), record.getLabel(),
                                                                                      Model.Histogram.ItemType.CLASS,
                                                                                      record.getNumberOfObjects(),
                                                                                      record.getUsedHeapSize(),
                                                                                      record.getRetainedHeapSize()))
                                              .sort(Model.Histogram.Item.sortBy(sortBy, ascendingOrder))
                                              .done();
                    } else {
                        return PageView.empty();
                    }
                }
                case BY_SUPERCLASS: {
                    Histogram.SuperclassTree st = (Histogram.SuperclassTree) result;
                    List<?> children = new ExoticTreeFinder(st)
                            .setGetChildrenCallback(node -> {
                                Map<String, ?> subClasses = ReflectionUtil.getFieldValueOrNull(node, "subClasses");
                                if (subClasses != null) {
                                    return new ArrayList<>(subClasses.values());
                                }
                                return null;
                            })
                            .setPredicate((theTree, theNode) -> theTree.getContext(theNode).getObjectId())
                            .findChildrenOf(parentObjectId);

                    if (children != null) {
                        return PageViewBuilder.<HistogramRecord, Model.Histogram.Item>fromList(
                                                      (List<HistogramRecord>) children)
                                              .paging(new PagingRequest(page, pageSize))
                                              .map(e -> {
                                                  Model.Histogram.Item item = new Model.Histogram.Item();
                                                  int objectId = st.getContext(e).getObjectId();
                                                  item.setType(Model.Histogram.ItemType.SUPER_CLASS);
                                                  item.setObjectId(objectId);
                                                  item.setLabel((String) st.getColumnValue(e, 0));
                                                  item.setNumberOfObjects((Long) st.getColumnValue(e, 1));
                                                  item.setShallowSize(((Bytes) st.getColumnValue(e, 2)).getValue());
                                                  return item;
                                              })
                                              .sort(Model.Histogram.Item.sortBy(sortBy, ascendingOrder))
                                              .done();
                    } else {
                        return PageView.empty();
                    }
                }
                case BY_PACKAGE: {
                    Histogram.PackageTree pt = (Histogram.PackageTree) result;
                    Object targetParentNode = new ExoticTreeFinder(pt)
                            .setGetChildrenCallback(node -> {
                                Map<String, ?> subPackages = ReflectionUtil.getFieldValueOrNull(node, "subPackages");
                                if (subPackages != null) {
                                    return new ArrayList<>(subPackages.values());
                                } else {
                                    return null;
                                }
                            })
                            .setPredicate((theTree, theNode) -> {
                                if (!(theNode instanceof XClassHistogramRecord)) {
                                    try {
                                        java.lang.reflect.Field
                                                field = theNode.getClass().getSuperclass().getDeclaredField("label");
                                        field.setAccessible(true);
                                        String labelName = (String) field.get(theNode);
                                        return labelName.hashCode();
                                    } catch (Throwable e) {
                                        e.printStackTrace();
                                    }
                                }
                                return null;
                            })
                            .findTargetParentNode(parentObjectId);
                    if (targetParentNode != null) {
                        Map<String, ?> packageMap = ReflectionUtil.getFieldValueOrNull(targetParentNode, "subPackages");
                        List<?> elems = new ArrayList<>();
                        if (packageMap != null) {
                            if (packageMap.size() == 0) {
                                elems = ReflectionUtil.getFieldValueOrNull(targetParentNode, "classes");
                            } else {
                                elems = new ArrayList<>(packageMap.values());
                            }
                        }
                        return
                                PageViewBuilder.<HistogramRecord, Model.Histogram.Item>fromList(
                                                       (List<HistogramRecord>) elems)
                                               .paging(new PagingRequest(page, pageSize))
                                               .map(e -> {

                                                   Model.Histogram.Item item = new Model.Histogram.Item();
                                                   String label = (String) pt.getColumnValue(e, 0);
                                                   item.setLabel(label);

                                                   if (e instanceof XClassHistogramRecord) {
                                                       int objectId = pt.getContext(e).getObjectId();
                                                       item.setObjectId(objectId);
                                                       item.setType(Model.Histogram.ItemType.CLASS);
                                                   } else {
                                                       item.setObjectId(label.hashCode());
                                                       item.setType(Model.Histogram.ItemType.PACKAGE);
                                                   }

                                                   if (label.matches("^int(\\[\\])*") || label.matches("^char(\\[\\])*") ||
                                                       label.matches("^byte(\\[\\])*") || label.matches("^short(\\[\\])*") ||
                                                       label.matches("^boolean(\\[\\])*") ||
                                                       label.matches("^double(\\[\\])*") ||
                                                       label.matches("^float(\\[\\])*") || label.matches("^long(\\[\\])*") ||
                                                       label.matches("^void(\\[\\])*")) {
                                                       item.setType(Model.Histogram.ItemType.CLASS);
                                                   }
                                                   item.setNumberOfObjects((Long) pt.getColumnValue(e, 1));
                                                   item.setShallowSize(((Bytes) pt.getColumnValue(e, 2)).getValue());

                                                   return item;
                                               })
                                               .sort(Model.Histogram.Item.sortBy(sortBy, ascendingOrder))
                                               .done();

                    } else {
                        return PageView.empty();
                    }
                }
                default: {
                    throw new AnalysisException("Should not reach here");
                }
            }
        });
    }

    private PageView<DominatorTree.DefaultItem> buildDefaultItems(ISnapshot snapshot, IResultTree tree,
                                                                  List<?> elements,
                                                                  boolean ascendingOrder, String sortBy,
                                                                  String searchText, SearchType searchType,
                                                                  PagingRequest pagingRequest, int parentObjectId) {
        final AtomicInteger afterFilterCount = new AtomicInteger(0);
        List<DominatorTree.DefaultItem> items = elements.stream()
                                                        .map(e -> $(() -> new VirtualDefaultItem(snapshot, tree, e, parentObjectId)))
                                                        .filter(SearchPredicate.createPredicate(searchText, searchType))
                                                        .peek(filtered -> afterFilterCount.incrementAndGet())
                                                        .sorted(DominatorTree.DefaultItem.sortBy(sortBy, ascendingOrder))
                                                        .skip(pagingRequest.from())
                                                        .limit(pagingRequest.getPageSize())
                                                        .collect(Collectors.toList());
        return new PageView<>(pagingRequest, afterFilterCount.get(), items);
    }

    private PageView<DominatorTree.ClassItem> buildClassItems(ISnapshot snapshot, IResultTree tree, List<?> elements,
                                                              boolean ascendingOrder,
                                                              String sortBy,
                                                              String searchText, SearchType searchType,
                                                              PagingRequest pagingRequest) {
        final AtomicInteger afterFilterCount = new AtomicInteger(0);
        List<DominatorTree.ClassItem> items = elements.stream()
                                                      .map(e -> $(() -> new VirtualClassItem(snapshot, tree, e)))
                                                      .filter(SearchPredicate.createPredicate(searchText, searchType))
                                                      .peek(filtered -> afterFilterCount.incrementAndGet())
                                                      .sorted(DominatorTree.ClassItem.sortBy(sortBy, ascendingOrder))
                                                      .skip(pagingRequest.from())
                                                      .limit(pagingRequest.getPageSize())
                                                      .collect(Collectors.toList());
        return new PageView<>(pagingRequest, afterFilterCount.get(), items);
    }

    private PageView<DominatorTree.ClassLoaderItem> buildClassLoaderItems(ISnapshot snapshot, IResultTree tree,
                                                                          List<?> elements, boolean ascendingOrder,
                                                                          String sortBy,
                                                                          String searchText, SearchType searchType,
                                                                          PagingRequest pagingRequest) {
        final AtomicInteger afterFilterCount = new AtomicInteger(0);
        List<DominatorTree.ClassLoaderItem> items = elements.stream()
                                                            .map(e -> $(() -> new VirtualClassLoaderItem(snapshot, tree, e)))
                                                            .filter(SearchPredicate.createPredicate(searchText, searchType))
                                                            .peek(filtered -> afterFilterCount.incrementAndGet())
                                                            .sorted(DominatorTree.ClassLoaderItem.sortBy(sortBy, ascendingOrder))
                                                            .skip(pagingRequest.from())
                                                            .limit(pagingRequest.getPageSize())
                                                            .collect(Collectors.toList());
        return new PageView<>(pagingRequest, afterFilterCount.get(), items);
    }

    private PageView<DominatorTree.PackageItem> buildPackageItems(ISnapshot snapshot, IResultTree tree,
                                                                  List<?> elements,
                                                                  boolean ascendingOrder, String sortBy,
                                                                  String searchText, SearchType searchType,
                                                                  PagingRequest pagingRequest) {
        final AtomicInteger afterFilterCount = new AtomicInteger(0);
        List<DominatorTree.PackageItem> items = elements.stream()
                                                        .map(e -> $(() -> new VirtualPackageItem(snapshot, tree, e)))
                                                        .filter(SearchPredicate.createPredicate(searchText, searchType))
                                                        .peek(filtered -> afterFilterCount.incrementAndGet())
                                                        .sorted(DominatorTree.PackageItem.sortBy(sortBy, ascendingOrder))
                                                        .skip(pagingRequest.from())
                                                        .limit(pagingRequest.getPageSize())
                                                        .collect(Collectors.toList());
        return new PageView<>(pagingRequest, afterFilterCount.get(), items);
    }

    @Override
    public PageView<? extends DominatorTree.Item> getRootsOfDominatorTree(DominatorTree.Grouping groupBy, String sortBy,
                                                                          boolean ascendingOrder, String searchText,
                                                                          SearchType searchType, int page,
                                                                          int pageSize) {
        return $(() -> {
            Map<String, Object> args = new HashMap<>();
            IResultTree tree = queryByCommand(context, "dominator_tree -groupBy " + groupBy.name(), args);
            switch (groupBy) {
                case NONE:
                    return
                            buildDefaultItems(context.snapshot, tree, tree.getElements(), ascendingOrder, sortBy,
                                              searchText, searchType, new PagingRequest(page, pageSize), -1);
                case BY_CLASS:
                    return buildClassItems(context.snapshot, tree, tree.getElements(), ascendingOrder, sortBy,
                                           searchText, searchType, new PagingRequest(page, pageSize));

                case BY_CLASSLOADER:
                    return buildClassLoaderItems(context.snapshot, tree, tree.getElements(), ascendingOrder, sortBy,
                                                 searchText, searchType, new PagingRequest(page, pageSize));
                case BY_PACKAGE:
                    return buildPackageItems(context.snapshot, tree, tree.getElements(), ascendingOrder, sortBy,
                                             searchText, searchType, new PagingRequest(page, pageSize));
                default:
                    throw new AnalysisException("Should not reach here");
            }
        });
    }

    @Override
    public PageView<? extends DominatorTree.Item> getChildrenOfDominatorTree(DominatorTree.Grouping groupBy,
                                                                             String sortBy, boolean ascendingOrder,
                                                                             int parentObjectId,
                                                                             int[] idPathInResultTree, int page,
                                                                             int pageSize) {
        return $(() -> {
            Map<String, Object> args = new HashMap<>();
            IResultTree tree = queryByCommand(context, "dominator_tree -groupBy " + groupBy.name(), args);
            switch (groupBy) {
                case NONE: {
                    Object parent = Helper.fetchObjectInResultTree(tree, idPathInResultTree);
                    return
                            buildDefaultItems(context.snapshot, tree, tree.getChildren(parent), ascendingOrder, sortBy,
                                              null, null, new PagingRequest(page, pageSize), parentObjectId);
                }
                case BY_CLASS: {
                    Object object = Helper.fetchObjectInResultTree(tree, idPathInResultTree);
                    List<?> elements = object == null ? Collections.emptyList() : tree.getChildren(object);
                    return buildClassItems(context.snapshot, tree, elements, ascendingOrder, sortBy, null, null, new PagingRequest(page
                            , pageSize));
                }
                case BY_CLASSLOADER: {
                    Object parent = Helper.fetchObjectInResultTree(tree, idPathInResultTree);
                    List<?> children = tree.getChildren(parent);

                    if (children != null) {
                        return buildClassLoaderItems(context.snapshot, tree, children, ascendingOrder, sortBy, null,
                                                     null, new PagingRequest(page, pageSize));
                    } else {
                        return PageView.empty();
                    }
                }
                case BY_PACKAGE: {
                    Object targetParentNode = new ExoticTreeFinder(tree)
                            .setGetChildrenCallback(node -> {
                                Map<String, ?> subPackages = ReflectionUtil.getFieldValueOrNull(node, "subPackages");
                                if (subPackages != null) {
                                    return new ArrayList<>(subPackages.values());
                                } else {
                                    return null;
                                }
                            })
                            .setPredicate((theTree, theNode) -> {
                                try {
                                    java.lang.reflect.Field
                                            field =
                                            theNode.getClass().getSuperclass().getSuperclass().getDeclaredField("label");
                                    field.setAccessible(true);
                                    String labelName = (String) field.get(theNode);
                                    return labelName.hashCode();
                                } catch (Throwable e) {
                                    e.printStackTrace();
                                }
                                return null;
                            })
                            .findTargetParentNode(parentObjectId);
                    if (targetParentNode != null) {
                        Map<String, ?> packageMap = ReflectionUtil.getFieldValueOrNull(targetParentNode, "subPackages");
                        List<?> elems = new ArrayList<>();
                        if (packageMap != null) {
                            if (packageMap.size() == 0) {
                                elems = ReflectionUtil.getFieldValueOrNull(targetParentNode, "classes");
                            } else {
                                elems = new ArrayList<>(packageMap.values());
                            }
                        }
                        if (elems != null) {
                            return
                                    buildPackageItems(context.snapshot, tree, elems, ascendingOrder, sortBy, null, null,
                                                      new PagingRequest(page, pageSize));
                        } else {
                            return PageView.empty();
                        }
                    } else {
                        return PageView.empty();
                    }
                }
                default:
                    throw new AnalysisException("Should not reach here");
            }
        });
    }

    interface R {
        void run() throws Exception;
    }

    interface RV<V> {
        V run() throws Exception;
    }

    private static class ProviderImpl implements HeapDumpAnalyzer.Provider {
        @Override
        public HeapDumpAnalyzer provide(Path path, Map<String, String> options,
                                        ProgressListener listener) {
            Map<String, String> finalOptions = options != null ? options : Collections.emptyMap();
            return new HeapDumpAnalyzerImpl(new AnalysisContext(
                    $(() ->
                      {
                          try {
                              HprofPreferencesAccess.setStrictness(finalOptions.get("strictness"));
                              return SnapshotFactory.openSnapshot(path.toFile(),
                                                                  finalOptions,
                                                                  new ProgressListenerImpl(listener));
                          } finally {
                              HprofPreferencesAccess.setStrictness(null);
                          }
                      })
            ));
        }
    }
}
