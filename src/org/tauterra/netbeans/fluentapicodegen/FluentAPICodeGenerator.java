/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.tauterra.netbeans.fluentapicodegen;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.source.CancellableTask;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.ModificationResult;
import org.netbeans.api.java.source.TreeMaker;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.spi.editor.codegen.CodeGenerator;
import org.netbeans.spi.editor.codegen.CodeGeneratorContextProvider;
import org.openide.util.Lookup;

public class FluentAPICodeGenerator implements CodeGenerator {

    JTextComponent textComp;

    public static final Logger LOG = Logger.getLogger(FluentAPICodeGenerator.class.getName());

    /**
     *
     * @param context containing JTextComponent and possibly other items
     * registered by {@link CodeGeneratorContextProvider}
     */
    private FluentAPICodeGenerator(Lookup context) { // Good practice is not to save Lookup outside ctor
        textComp = context.lookup(JTextComponent.class);
    }

    @MimeRegistration(mimeType = "text/x-java", service = CodeGenerator.Factory.class)
    public static class Factory implements CodeGenerator.Factory {

        @Override
        public List<? extends CodeGenerator> create(Lookup context) {
            return Collections.singletonList(new FluentAPICodeGenerator(context));
        }
    }

    /**
     * The name which will be inserted inside Insert Code dialog
     */
    @Override
    public String getDisplayName() {
        return "Fluent API";
    }

    /**
     * This will be invoked when user chooses this Generator from Insert Code
     * dialog
     */
    @Override
    public void invoke() {
        Logger.getLogger(FluentAPICodeGenerator.class.getName()).info("generate fluent API");
        try {
            Document doc = textComp.getDocument();
            JavaSource javaSource = JavaSource.forDocument(doc);
            CancellableTask<WorkingCopy> task = new CancellableTask<WorkingCopy>() {
                public void run(WorkingCopy workingCopy) throws IOException {
                    workingCopy.toPhase(Phase.RESOLVED);
                    CompilationUnitTree cut = workingCopy.getCompilationUnit();
                    TreeMaker make = workingCopy.getTreeMaker();
                    for (Tree typeDecl : cut.getTypeDecls()) {
                        if (Tree.Kind.CLASS == typeDecl.getKind()) {
                            ClassTree clazz = (ClassTree) typeDecl;

                            Map<String, GenPlanItem> genPlan = new HashMap<>();

                            for (Tree m : clazz.getMembers()) {
                                if (Tree.Kind.VARIABLE.equals(m.getKind())) {
                                    VariableTree vrble = (VariableTree) m;
                                    GenPlanItem item = new GenPlanItem();
                                    item.memberName = vrble.getName();
                                    item.memberType = vrble.getType();
                                    item.isFinal = vrble.getModifiers().getFlags().contains(Modifier.FINAL);
                                    genPlan.put(item.memberName.toString(), item);
                                }
                            }

                            for (Tree m : clazz.getMembers()) {
                                if (Tree.Kind.METHOD.equals(m.getKind())) {
                                    MethodTree mthd = (MethodTree) m;
                                    String methodName = mthd.getName().toString();

                                    if (genPlan.containsKey(methodName)) {
                                        GenPlanItem genItem = genPlan.get(methodName);
                                        List<? extends VariableTree> params = mthd.getParameters();
                                        Tree returnTypeTree = mthd.getReturnType();
                                        String returnType = returnTypeTree.toString();
                                        String returnTypeTest = genItem.memberType.toString();

                                        if (params.isEmpty() && returnType.equals(returnTypeTest)) {
                                            genItem.hasGetter = true;
                                        } else if (params.size() == 1
                                                && params.get(0).getType().toString().equals(returnTypeTest)
                                                && returnType.equals(clazz.getSimpleName().toString())) {
                                            genItem.hasSetter = true;
                                        }

                                    }
                                }
                            }

//                            for (Entry<String, GenPlanItem> itm : genPlan.entrySet()) {
//                                LOG.info(itm.toString());
//                            }
                            ClassTree modifiedClazz = clazz;
                            for (GenPlanItem itm : genPlan.values()) {
                                if (!itm.hasGetter) {
                                    // create getter
                                    // public <member_type> memberName() { return this.<member_name>; }
                                    ModifiersTree methodModifiers
                                            = make.Modifiers(Collections.<Modifier>singleton(Modifier.PUBLIC),
                                                    Collections.<AnnotationTree>emptyList());

                                    MessageFormat getterFmt = new MessageFormat("'{' return this.{0}; '}'");

                                    MethodTree getterMethod
                                            = make.Method(
                                                    methodModifiers,
                                                    itm.memberName,
                                                    itm.memberType,
                                                    Collections.<TypeParameterTree>emptyList(),
                                                    Collections.<VariableTree>emptyList(),
                                                    Collections.<ExpressionTree>emptyList(),
                                                    getterFmt.format(new Object[]{itm.memberName.toString()}),
                                                    null);
                                    modifiedClazz = make.addClassMember(modifiedClazz, getterMethod);
                                }
                                if (!itm.hasSetter & !itm.isFinal) {
                                    // create setter
                                    // public <clazz> memberName(<member_type> value) { this.<member_name> = value; return this; }

                                    ModifiersTree methodModifiers
                                            = make.Modifiers(Collections.<Modifier>singleton(Modifier.PUBLIC),
                                                    Collections.<AnnotationTree>emptyList());

                                    MessageFormat setterFmt = new MessageFormat("'{' this.{0} = value; return this; '}'");

                                    VariableTree parameter
                                            = make.Variable(make.Modifiers(Collections.<Modifier>emptySet(),
                                                    Collections.<AnnotationTree>emptyList()),
                                                    "value",
                                                    make.Identifier(itm.memberType.toString()),
                                                    null);

                                    MethodTree setterMethod
                                            = make.Method(
                                                    methodModifiers,
                                                    itm.memberName,
                                                    make.Identifier(clazz.getSimpleName()),
                                                    Collections.<TypeParameterTree>emptyList(),
                                                    Collections.singletonList(parameter),
                                                    Collections.<ExpressionTree>emptyList(),
                                                    setterFmt.format(new Object[]{itm.memberName.toString()}),
                                                    null);
                                    modifiedClazz = make.addClassMember(modifiedClazz, setterMethod);
                                }
                            }
                            workingCopy.rewrite(clazz, modifiedClazz);
                        }
                    }
                }

                public void cancel() {
                }
            };
            ModificationResult result = javaSource.runModificationTask(task);
            result.commit();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        } finally {
        }
    }

    private static class GenPlanItem {

        private Name memberName;
        private Tree memberType;
        private boolean hasSetter = false;
        private boolean hasGetter = false;
        private boolean isFinal = false;

        @Override
        public String toString() {
            return "GenPlanItem{" + "memberName=" + memberName + ", memberType=" + memberType + ", hasSetter=" + hasSetter + ", hasGetter=" + hasGetter + ", isFinal=" + isFinal + '}';
        }

    }

}
