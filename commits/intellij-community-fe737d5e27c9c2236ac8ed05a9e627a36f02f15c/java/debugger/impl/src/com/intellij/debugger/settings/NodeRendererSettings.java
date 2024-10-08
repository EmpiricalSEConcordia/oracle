// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.Renderer;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@State(name = "NodeRendererSettings", storages = {
  @Storage("debugger.xml"),
  @Storage(value = "debugger.renderers.xml", deprecated = true),
})
public class NodeRendererSettings implements PersistentStateComponent<Element> {
  @NonNls private static final String REFERENCE_RENDERER = "Reference renderer";
  @NonNls public static final String RENDERER_TAG = "Renderer";
  @NonNls private static final String RENDERER_ID = "ID";

  private final EventDispatcher<NodeRendererSettingsListener> myDispatcher = EventDispatcher.create(NodeRendererSettingsListener.class);
  private final RendererConfiguration myCustomRenderers = new RendererConfiguration(this);

  // base renderers
  private final PrimitiveRenderer myPrimitiveRenderer = new PrimitiveRenderer();
  private final ArrayRenderer myArrayRenderer = new ArrayRenderer();
  private final ClassRenderer myClassRenderer = new ClassRenderer();
  private final HexRenderer myHexRenderer = new HexRenderer();
  private final ToStringRenderer myToStringRenderer = new ToStringRenderer();
  // alternate collections
  private final NodeRenderer[] myAlternateCollectionRenderers = new NodeRenderer[]{
    createCompoundReferenceRenderer(
      "Map", CommonClassNames.JAVA_UTIL_MAP,
      createLabelRenderer(" size = ", "size()", null),
      createExpressionArrayChildrenRenderer("entrySet().toArray()", "!isEmpty()", myArrayRenderer)
    ),
    createCompoundReferenceRenderer(
      "Map.Entry", "java.util.Map$Entry",
      new MapEntryLabelRenderer()/*createLabelRenderer(null, "\" \" + getKey() + \" -> \" + getValue()", null)*/,
      createEnumerationChildrenRenderer(new String[][]{{"key", "getKey()"}, {"value", "getValue()"}})
    ),
    new ListObjectRenderer(this, myArrayRenderer),
    createCompoundReferenceRenderer(
      "Collection", "java.util.Collection",
      createLabelRenderer(" size = ", "size()", null),
      createExpressionArrayChildrenRenderer("toArray()", "!isEmpty()", myArrayRenderer)
    )
  };
  @NonNls private static final String HEX_VIEW_ENABLED = "HEX_VIEW_ENABLED";
  @NonNls private static final String ALTERNATIVE_COLLECTION_VIEW_ENABLED = "ALTERNATIVE_COLLECTION_VIEW_ENABLED";
  @NonNls private static final String CUSTOM_RENDERERS_TAG_NAME = "CustomRenderers";

  public NodeRendererSettings() {
    // default configuration
    myHexRenderer.setEnabled(false);
    setAlternateCollectionViewsEnabled(true);
  }

  public static NodeRendererSettings getInstance() {
    return ServiceManager.getService(NodeRendererSettings.class);
  }

  public void setAlternateCollectionViewsEnabled(boolean enabled) {
    for (NodeRenderer myAlternateCollectionRenderer : myAlternateCollectionRenderers) {
      myAlternateCollectionRenderer.setEnabled(enabled);
    }
  }

  public boolean areAlternateCollectionViewsEnabled() {
    return myAlternateCollectionRenderers[0].isEnabled();
  }

  public boolean equals(Object o) {
    if(!(o instanceof NodeRendererSettings)) return false;

    return DebuggerUtilsEx.elementsEqual(getState(), ((NodeRendererSettings)o).getState());
  }

  public void addListener(NodeRendererSettingsListener listener, Disposable disposable) {
    myDispatcher.addListener(listener, disposable);
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public Element getState()  {
    final Element element = new Element("state");
    if (myHexRenderer.isEnabled()) {
      JDOMExternalizerUtil.writeField(element, HEX_VIEW_ENABLED, "true");
    }
    if (!areAlternateCollectionViewsEnabled()) {
      JDOMExternalizerUtil.writeField(element, ALTERNATIVE_COLLECTION_VIEW_ENABLED, "false");
    }

    try {
      element.addContent(writeRenderer(myToStringRenderer));
      element.addContent(writeRenderer(myClassRenderer));
      element.addContent(writeRenderer(myPrimitiveRenderer));
      if (myCustomRenderers.getRendererCount() > 0) {
        final Element custom = new Element(CUSTOM_RENDERERS_TAG_NAME);
        element.addContent(custom);
        myCustomRenderers.writeExternal(custom);
      }
    }
    catch (WriteExternalException ignore) {
    }
    return element;
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public void loadState(@NotNull final Element root) {
    final String hexEnabled = JDOMExternalizerUtil.readField(root, HEX_VIEW_ENABLED);
    if (hexEnabled != null) {
      myHexRenderer.setEnabled(Boolean.parseBoolean(hexEnabled));
    }

    final String alternativeEnabled = JDOMExternalizerUtil.readField(root, ALTERNATIVE_COLLECTION_VIEW_ENABLED);
    if (alternativeEnabled != null) {
      setAlternateCollectionViewsEnabled(Boolean.parseBoolean(alternativeEnabled));
    }

    for (final Element elem : root.getChildren(RENDERER_TAG)) {
      final String id = elem.getAttributeValue(RENDERER_ID);
      if (id == null) {
        continue;
      }
      try {
        if (ToStringRenderer.UNIQUE_ID.equals(id)) {
          myToStringRenderer.readExternal(elem);
          if (!myToStringRenderer.isEnabled()) {
            myToStringRenderer.setEnabled(true);
            myToStringRenderer.setOnDemand(true);
          }
        }
        else if (ClassRenderer.UNIQUE_ID.equals(id)) {
          myClassRenderer.readExternal(elem);
        }
        else if (PrimitiveRenderer.UNIQUE_ID.equals(id)) {
          myPrimitiveRenderer.readExternal(elem);
        }
      }
      catch (InvalidDataException e) {
        // ignore
      }
    }
    final Element custom = root.getChild(CUSTOM_RENDERERS_TAG_NAME);
    if (custom != null) {
      myCustomRenderers.readExternal(custom);
    }

    myDispatcher.getMulticaster().renderersChanged();
  }

  public RendererConfiguration getCustomRenderers() {
    return myCustomRenderers;
  }

  public PrimitiveRenderer getPrimitiveRenderer() {
    return myPrimitiveRenderer;
  }

  public ArrayRenderer getArrayRenderer() {
    return myArrayRenderer;
  }

  public ClassRenderer getClassRenderer() {
    return myClassRenderer;
  }

  public HexRenderer getHexRenderer() {
    return myHexRenderer;
  }

  public ToStringRenderer getToStringRenderer() {
    return myToStringRenderer;
  }

  public NodeRenderer[] getAlternateCollectionRenderers() {
    return myAlternateCollectionRenderers;
  }

  public void fireRenderersChanged() {
    myDispatcher.getMulticaster().renderersChanged();
  }

  public List<NodeRenderer> getAllRenderers() {
    // the order is important as the renderers are applied according to it
    final List<NodeRenderer> allRenderers = new ArrayList<>();

    // user defined renderers must come first
    myCustomRenderers.iterateRenderers(renderer -> {
      allRenderers.add(renderer);
      return true;
    });

    // plugins registered renderers come after that
    Collections.addAll(allRenderers, NodeRenderer.EP_NAME.getExtensions());

    // now all predefined stuff
    allRenderers.add(myHexRenderer);
    allRenderers.add(myPrimitiveRenderer);
    Collections.addAll(allRenderers, myAlternateCollectionRenderers);
    allRenderers.add(myToStringRenderer);
    allRenderers.add(myArrayRenderer);
    allRenderers.add(myClassRenderer);
    return allRenderers;
  }

  public Renderer readRenderer(Element root) throws InvalidDataException {
    if (root == null) {
      return null;
    }

    if (!RENDERER_TAG.equals(root.getName())) {
      throw new InvalidDataException("Cannot read renderer - tag name is not '" + RENDERER_TAG + "'");
    }

    final String rendererId = root.getAttributeValue(RENDERER_ID);
    if(rendererId == null) {
      throw new InvalidDataException("unknown renderer ID: " + rendererId);
    }

    final Renderer renderer = createRenderer(rendererId);
    if(renderer == null) {
      throw new InvalidDataException("unknown renderer ID: " + rendererId);
    }

    renderer.readExternal(root);

    return renderer;
  }

  @NotNull
  public Element writeRenderer(Renderer renderer) throws WriteExternalException {
    Element root = new Element(RENDERER_TAG);
    if (renderer != null) {
      root.setAttribute(RENDERER_ID, renderer.getUniqueId());
      renderer.writeExternal(root);
    }
    return root;
  }

  public Renderer createRenderer(final String rendererId) {
    if (ClassRenderer.UNIQUE_ID.equals(rendererId)) {
      return myClassRenderer;
    }
    else if (ArrayRenderer.UNIQUE_ID.equals(rendererId)) {
      return myArrayRenderer;
    }
    else if (PrimitiveRenderer.UNIQUE_ID.equals(rendererId)) {
      return myPrimitiveRenderer;
    }
    else if(HexRenderer.UNIQUE_ID.equals(rendererId)) {
      return myHexRenderer;
    }
    else if(rendererId.equals(ExpressionChildrenRenderer.UNIQUE_ID)) {
      return new ExpressionChildrenRenderer();
    }
    else if(rendererId.equals(LabelRenderer.UNIQUE_ID)) {
      return new LabelRenderer();
    }
    else if(rendererId.equals(EnumerationChildrenRenderer.UNIQUE_ID)) {
      return new EnumerationChildrenRenderer();
    }
    else if(rendererId.equals(ToStringRenderer.UNIQUE_ID)) {
      return myToStringRenderer;
    }
    else if(rendererId.equals(CompoundNodeRenderer.UNIQUE_ID) || rendererId.equals(REFERENCE_RENDERER)) {
      return createCompoundReferenceRenderer("unnamed", CommonClassNames.JAVA_LANG_OBJECT, null, null);
    }
    else if (rendererId.equals(CompoundTypeRenderer.UNIQUE_ID)) {
      return createCompoundTypeRenderer("unnamed", CommonClassNames.JAVA_LANG_OBJECT, null, null);
    }
    return null;
  }

  public CompoundTypeRenderer createCompoundTypeRenderer(
    @NonNls final String rendererName, @NonNls final String className, final ValueLabelRenderer labelRenderer, final ChildrenRenderer childrenRenderer
  ) {
    CompoundTypeRenderer renderer = new CompoundTypeRenderer(this, rendererName, labelRenderer, childrenRenderer);
    renderer.setClassName(className);
    return renderer;
  }

  public CompoundReferenceRenderer createCompoundReferenceRenderer(
    @NonNls final String rendererName, @NonNls final String className, final ValueLabelRenderer labelRenderer, final ChildrenRenderer childrenRenderer
    ) {
    CompoundReferenceRenderer renderer = new CompoundReferenceRenderer(this, rendererName, labelRenderer, childrenRenderer);
    renderer.setClassName(className);
    return renderer;
  }

  private static ExpressionChildrenRenderer createExpressionArrayChildrenRenderer(String expressionText,
                                                                                  String childrenExpandableText,
                                                                                  ArrayRenderer arrayRenderer) {
    ExpressionChildrenRenderer renderer = createExpressionChildrenRenderer(expressionText, childrenExpandableText);
    renderer.setPredictedRenderer(arrayRenderer);
    return renderer;
  }

  public static ExpressionChildrenRenderer createExpressionChildrenRenderer(@NonNls String expressionText,
                                                                             @NonNls String childrenExpandableText) {
    final ExpressionChildrenRenderer childrenRenderer = new ExpressionChildrenRenderer();
    childrenRenderer.setChildrenExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expressionText, "", StdFileTypes.JAVA));
    if (childrenExpandableText != null) {
      childrenRenderer.setChildrenExpandable(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, childrenExpandableText, "", StdFileTypes.JAVA));
    }
    return childrenRenderer;
  }

  public static EnumerationChildrenRenderer createEnumerationChildrenRenderer(@NonNls String[][] expressions) {
    EnumerationChildrenRenderer childrenRenderer = new EnumerationChildrenRenderer();
    if (expressions != null && expressions.length > 0) {
      ArrayList<EnumerationChildrenRenderer.ChildInfo> childrenList = new ArrayList<>(expressions.length);
      for (String[] expression : expressions) {
        childrenList.add(new EnumerationChildrenRenderer.ChildInfo(
          expression[0], new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expression[1], "", StdFileTypes.JAVA), false));
      }
      childrenRenderer.setChildren(childrenList);
    }
    return childrenRenderer;
  }

  private static LabelRenderer createLabelRenderer(@NonNls final String prefix, @NonNls final String expressionText, @NonNls final String postfix) {
    final LabelRenderer labelRenderer = new LabelRenderer() {
      @Override
      public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener) throws EvaluateException {
        final String evaluated = super.calcLabel(descriptor, evaluationContext, labelListener);
        if (prefix == null && postfix == null) {
          return evaluated;
        }
        if (prefix != null && postfix != null) {
          return prefix + evaluated + postfix;
        }
        if (prefix != null) {
          return prefix + evaluated;
        }
        return evaluated + postfix;
      }
    };
    labelRenderer.setLabelExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expressionText, "", StdFileTypes.JAVA));
    return labelRenderer;
  }

  private static class MapEntryLabelRenderer extends ReferenceRenderer implements ValueLabelRenderer{
    private static final Computable<String> NULL_LABEL_COMPUTABLE = () -> "null";

    private final MyCachedEvaluator myKeyExpression = new MyCachedEvaluator();
    private final MyCachedEvaluator myValueExpression = new MyCachedEvaluator();

    private MapEntryLabelRenderer() {
      super("java.util.Map$Entry");
      myKeyExpression.setReferenceExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "this.getKey()", "", StdFileTypes.JAVA));
      myValueExpression.setReferenceExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "this.getValue()", "", StdFileTypes.JAVA));
    }

    @Override
    public Icon calcValueIcon(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) {
      return null;
    }

    @Override
    public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) throws EvaluateException {
      final DescriptorUpdater descriptorUpdater = new DescriptorUpdater(descriptor, listener);

      final Value originalValue = descriptor.getValue();
      final Pair<Computable<String>, ValueDescriptorImpl> keyPair = createValueComputable(evaluationContext, originalValue, myKeyExpression, descriptorUpdater);
      final Pair<Computable<String>, ValueDescriptorImpl> valuePair = createValueComputable(evaluationContext, originalValue, myValueExpression, descriptorUpdater);

      descriptorUpdater.setKeyDescriptor(keyPair.second);
      descriptorUpdater.setValueDescriptor(valuePair.second);

      return DescriptorUpdater.constructLabelText(keyPair.first.compute(), valuePair.first.compute());
    }

    private Pair<Computable<String>, ValueDescriptorImpl> createValueComputable(final EvaluationContext evaluationContext,
                                                                                Value originalValue,
                                                                                final MyCachedEvaluator evaluator,
                                                                                final DescriptorLabelListener listener) throws EvaluateException {
      final Value eval = doEval(evaluationContext, originalValue, evaluator);
      if (eval != null) {
        final WatchItemDescriptor evalDescriptor = new WatchItemDescriptor(evaluationContext.getProject(), evaluator.getReferenceExpression(), eval);
        evalDescriptor.setShowIdLabel(false);
        return new Pair<>(() -> {
          evalDescriptor.updateRepresentation((EvaluationContextImpl)evaluationContext, listener);
          return evalDescriptor.getValueLabel();
        }, evalDescriptor);
      }
      return new Pair<>(NULL_LABEL_COMPUTABLE, null);
    }

    @Override
    public String getUniqueId() {
      return "MapEntry renderer";
    }

    private Value doEval(EvaluationContext evaluationContext, Value originalValue, MyCachedEvaluator cachedEvaluator)
      throws EvaluateException {
      final DebugProcess debugProcess = evaluationContext.getDebugProcess();
      if (originalValue == null) {
        return null;
      }
      try {
        final ExpressionEvaluator evaluator = cachedEvaluator.getEvaluator(debugProcess.getProject());
        if(!debugProcess.isAttached()) {
          throw EvaluateExceptionUtil.PROCESS_EXITED;
        }
        final EvaluationContext thisEvaluationContext = evaluationContext.createEvaluationContext(originalValue);
        return evaluator.evaluate(thisEvaluationContext);
      }
      catch (final EvaluateException ex) {
        throw new EvaluateException(DebuggerBundle.message("error.unable.to.evaluate.expression") + " " + ex.getMessage(), ex);
      }
    }

    private class MyCachedEvaluator extends CachedEvaluator {
      @Override
      protected String getClassName() {
        return MapEntryLabelRenderer.this.getClassName();
      }

      @Override
      public ExpressionEvaluator getEvaluator(Project project) throws EvaluateException {
        return super.getEvaluator(project);
      }
    }
  }

  private static class ListObjectRenderer extends CompoundReferenceRenderer {
    public ListObjectRenderer(NodeRendererSettings rendererSettings, ArrayRenderer arrayRenderer) {
      super(rendererSettings,
            "List",
            createLabelRenderer(" size = ", "size()", null),
            createExpressionArrayChildrenRenderer("toArray()", "!isEmpty()", arrayRenderer));
      setClassName(CommonClassNames.JAVA_UTIL_LIST);
    }

    @Override
    public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
      LOG.assertTrue(node.getDescriptor() instanceof ArrayElementDescriptorImpl);
      try {
        return getChildValueExpression("this.get(" + ((ArrayElementDescriptorImpl)node.getDescriptor()).getIndex() + ")", node, context);
      }
      catch (IncorrectOperationException e) {
        // fallback to original
        return super.getChildValueExpression(node, context);
      }
    }
  }

  private static class DescriptorUpdater implements DescriptorLabelListener {
    private final ValueDescriptor myTargetDescriptor;
    @Nullable
    private ValueDescriptorImpl myKeyDescriptor;
    @Nullable
    private ValueDescriptorImpl myValueDescriptor;
    private final DescriptorLabelListener myDelegate;

    private DescriptorUpdater(ValueDescriptor descriptor, DescriptorLabelListener delegate) {
      myTargetDescriptor = descriptor;
      myDelegate = delegate;
    }

    public void setKeyDescriptor(@Nullable ValueDescriptorImpl keyDescriptor) {
      myKeyDescriptor = keyDescriptor;
    }

    public void setValueDescriptor(@Nullable ValueDescriptorImpl valueDescriptor) {
      myValueDescriptor = valueDescriptor;
    }

    @Override
    public void labelChanged() {
      myTargetDescriptor.setValueLabel(constructLabelText(getDescriptorLabel(myKeyDescriptor), getDescriptorLabel(myValueDescriptor)));
      myDelegate.labelChanged();
    }

    static String constructLabelText(final String keylabel, final String valueLabel) {
      StringBuilder sb = new StringBuilder();
      sb.append('\"').append(keylabel).append("\" -> ");
      if (!StringUtil.isEmpty(valueLabel)) {
        sb.append('\"').append(valueLabel).append('\"');
      }
      return sb.toString();
    }

    private static String getDescriptorLabel(final ValueDescriptorImpl keyDescriptor) {
      return keyDescriptor == null? "null" : keyDescriptor.getValueLabel();
    }
  }
}
