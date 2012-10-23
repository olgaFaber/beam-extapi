/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.extapi.gen;

import com.sun.javadoc.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author Norman Fomferra
 * @version $Revision$ $Date$
 */
public class ExtApiDoclet extends Doclet {
    private RootDoc rootDoc;
    private ArrayList<ApiClass> wrappedClasses;
    private Set<ApiClass> usedClasses;
    private Set<String> wrappedClassNames;
    private Map<String, List<ApiMethod>> wrappedClassMethods;


    public static void main(String[] args) {

        com.sun.tools.javadoc.Main.main(new String[]{
                "-doclet", ExtApiDoclet.class.getName(),
                "-sourcepath", "" +
                "../beam/beam/beam-core/src/main/java;" +
                "../beam/beam/beam-gpf/src/main/java",
                "org.esa.beam.framework.datamodel",
                "org.esa.beam.framework.dataio",
                "org.esa.beam.framework.gpf",
                "org.esa.beam.util",
        });
    }

    private ExtApiDoclet(RootDoc rootDoc) throws IOException {
        this.rootDoc = rootDoc;
        wrappedClasses = new ArrayList<ApiClass>(100);
        wrappedClassMethods = new HashMap<String, List<ApiMethod>>(100);
        usedClasses = new HashSet<ApiClass>();

        final Properties properties = new Properties();
        properties.load(getClass().getResourceAsStream("ExtApiClasses.txt"));
        wrappedClassNames = properties.stringPropertyNames();
    }

    private void start() throws Exception {
        collectAll();
        writeHeader();
        writeSource();
        printStats();
    }

    private void printStats() {
        int numClasses = 0;
        int numMethods = 0;
        for (ApiClass wrappedClass : wrappedClasses) {
            numClasses++;
            numMethods += wrappedClassMethods.get(wrappedClass.getJavaName()).size();
        }
        System.out.printf("#Classes: %d, #Methods: %d\n", numClasses, numMethods);
    }

    private void writeSource() throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter("beam_wrappers.c"));
        try {
            generateFileInfo(writer);
            writer.write("#include \"beam_wrappers.h\"\n");
            writer.write("#include \"jni.h\"\n");
            writer.write("\n");
            for (ApiClass wrappedClass : wrappedClasses) {
                writer.write(String.format("static jclass %s_class;\n", wrappedClass.getExternalName()));
            }
            writer.write("\n");

            /////////////////////////////////////////////////////////////////////////////////////
            // beam_init_api()
            //
            writer.write("int beam_init_api()\n{\n");
            int errCode = 1000;
            for (ApiClass wrappedClass : wrappedClasses) {
                writer.write(String.format("    %s_class = (*jenv)->FindClass(jenv, \"%s\");\n",
                                           wrappedClass.getExternalName(), wrappedClass.getResourceName()));
                writer.write(String.format("    if (%s_class == NULL) return %d;\n",
                                           wrappedClass.getExternalName(), errCode));
                writer.write("\n");
                errCode++;
            }
            writer.write("    return 0;\n");
            writer.write("}\n\n");

            /////////////////////////////////////////////////////////////////////////////////////
            // Generate function code
            //
            for (ApiClass wrappedClass : wrappedClasses) {
                List<ApiMethod> apiMethods = wrappedClassMethods.get(wrappedClass.getJavaName());
                for (ApiMethod apiMethod : apiMethods) {
                    generateApiMethod(apiMethod, writer);
                }
            }
        } finally {
            writer.close();
        }
    }

    private void generateApiMethod(ApiMethod apiMethod, PrintWriter writer) throws IOException {

        generateFunctionSignature(apiMethod, writer);

        String returnType = apiMethod.getMethodDoc().returnType().simpleTypeName();
        boolean isVoid = "void".equals(returnType);

        writer.printf("\n{\n");
        writer.printf("    static jmethodID method = NULL;\n");
        if (!isVoid) {
            writer.printf("    %s _result;\n", returnType);
        }
        writer.printf("\n");
        writer.printf("    if (method == NULL) {\n");
        writer.printf("        method = (*jenv)->%s(jenv, %s_class, \"%s\", \"%s\");\n",
                      apiMethod.getMethodDoc().isStatic() ? "GetStaticMethodID" : "GetMethodID",
                      apiMethod.getApiClass().getExternalName(),
                      apiMethod.getJavaName(),
                      apiMethod.getJavaSignature());
        if (isVoid) {
            writer.printf("        if (method == NULL) return;\n");
        } else {
            writer.printf("        if (method == NULL) return 0;\n");
        }
        writer.printf("    }\n");
        writer.printf("\n");

        StringBuilder argList = new StringBuilder();
        if (!apiMethod.getMethodDoc().isStatic()) {
            argList.append("_self");
        }
        Parameter[] parameters = apiMethod.getMethodDoc().parameters();
        for (Parameter parameter : parameters) {
            if (argList.length() > 0) {
                argList.append(", ");
            }
            argList.append(parameter.name());
        }

        if (isVoid) {
            writer.printf("    (*jenv)->%s(jenv, %s_class, method, %s);\n",
                          apiMethod.getMethodDoc().isStatic() ? "CallStaticVoidMethod" : "CallVoidMethod",
                          apiMethod.getApiClass().getExternalName(),
                          argList);
        } else {
            writer.printf("    _result = (*jenv)->%s(jenv, %s_class, method, %s);\n",
                          apiMethod.getMethodDoc().isStatic() ? "CallStaticObjectMethod" : "CallObjectMethod",
                          apiMethod.getApiClass().getExternalName(),
                          argList);
        }

        if (!isVoid) {
            writer.printf("    return _result;\n");
        }

        writer.printf("}\n");
        writer.printf("\n");
    }

    private void generateFunctionSignature(ApiMethod apiMethod, PrintWriter writer) {
        StringBuilder parameterList = new StringBuilder();

        if (!apiMethod.getMethodDoc().isStatic()) {
            parameterList.append(apiMethod.getApiClass().getExternalName());
            parameterList.append(" _self");
        }
        Parameter[] parameters = apiMethod.getMethodDoc().parameters();
        for (Parameter parameter : parameters) {
            if (parameterList.length() > 0) {
                parameterList.append(", ");
            }
            parameterList.append(parameter.typeName());
            parameterList.append(" ");
            parameterList.append(parameter.name());
        }


        writer.printf("%s %s(%s)",
                      apiMethod.getMethodDoc().returnType().simpleTypeName(),
                      apiMethod.getExternalName(),
                      parameterList);
    }

    private void writeHeader() throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter("beam_wrappers.h"));
        try {
            generateFileInfo(writer);

            writer.write("\n");
            writer.write("/* Wrapped API classes */\n");
            for (ApiClass wrappedClass : wrappedClasses) {
                writer.write(String.format("typedef void* %s;\n", wrappedClass.getExternalName()));
            }
            writer.write("\n");

            writer.write("\n");
            writer.write("/* Non-API classes used in the API */\n");
            for (ApiClass usedClass : usedClasses) {
                if (!wrappedClasses.contains(usedClass)) {
                    writer.write(String.format("typedef void* %s;\n", usedClass.getExternalName()));
                }
            }
            writer.write("\n");

            /////////////////////////////////////////////////////////////////////////////////////
            // Generate function declarations
            //
            for (ApiClass wrappedClass : wrappedClasses) {
                writer.write("\n");
                writer.printf("/* Functions for class %s */\n", wrappedClass.getExternalName());
                writer.write("\n");
                List<ApiMethod> apiMethods = wrappedClassMethods.get(wrappedClass.getJavaName());
                for (ApiMethod apiMethod : apiMethods) {
                    generateFunctionDeclaration(writer, apiMethod);
                }
            }
        } finally {
            writer.close();
        }
    }

    private void generateFileInfo(PrintWriter writer) {
        writer.write(String.format("/*\n" +
                                           " * DO NOT EDIT THIS FILE, IT IS MACHINE-GENERATED\n" +
                                           " * File generated at %s using %s\n" +
                                           " */\n", new Date(), getClass().getName()));
        writer.write("\n");
    }

    private void generateFunctionDeclaration(PrintWriter writer, ApiMethod apiMethod) {
        generateFunctionSignature(apiMethod, writer);
        writer.write(";\n");
    }

    private void collectAll() throws ClassNotFoundException, NoSuchMethodException {
        final ClassDoc[] classDocs = rootDoc.classes();
        for (ClassDoc classDoc : classDocs) {

            final MethodDoc[] methodDocs = classDoc.methods();

            if (classDoc.isPublic() && wrappedClassNames.contains(classDoc.qualifiedTypeName())) {

                ApiClass apiClass = new ApiClass(classDoc);
                wrappedClasses.add(apiClass);
                ArrayList<ApiMethod> apiMethods = new ArrayList<ApiMethod>(64);
                wrappedClassMethods.put(apiClass.getJavaName(), apiMethods);

                for (MethodDoc methodDoc : methodDocs) {
                    if (methodDoc.isPublic()) {

                        Tag[] deprecatedTags = methodDoc.tags("deprecated");
                        if (deprecatedTags.length == 0) {

                            final Type retType = methodDoc.returnType();

                            apiMethods.add(new ApiMethod(apiClass, methodDoc));

                            if (!retType.isPrimitive()) {
                                usedClasses.add(new ApiClass(retType));
                            }

                            final Parameter[] parameters = methodDoc.parameters();
                            for (Parameter parameter : parameters) {
                                final Type type = parameter.type();
                                if (!type.isPrimitive()) {
                                    usedClasses.add(new ApiClass(type));
                                }
                            }
                        } else {
                            System.out.printf("Ignored deprecated method: %s.%s\n", classDoc.qualifiedTypeName(), methodDoc.name());
                        }
                    }
                }

            } else {
                System.out.printf("Ignored non-API class: %s\n", classDoc.qualifiedTypeName());
            }
        }
        Collections.sort(this.wrappedClasses);
    }

    @SuppressWarnings("UnusedDeclaration")
    public static boolean start(RootDoc root) {
        try {
            new ExtApiDoclet(root).start();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String mapTypeName(Type type) {
        final String simpleTypeName = type.simpleTypeName();
        if (simpleTypeName.equals("String")) {
            return "char*";
        }
        return type.isPrimitive() ? simpleTypeName : simpleTypeName + "*";
    }

    @SuppressWarnings("UnusedDeclaration")
    public static int optionLength(String optionName) {
        return 0;
    }

    @SuppressWarnings("UnusedDeclaration")
    public static boolean validOptions(String[][] options,
                                       DocErrorReporter docErrorReporter) {
        for (int i = 0; i < options.length; i++) {
            for (int j = 0; j < options[i].length; j++) {
                docErrorReporter.printWarning("options[" + i + "][" + j + "] = " + options[i][j]);
            }
        }
        return true;
    }

    @SuppressWarnings("UnusedDeclaration")
    public static LanguageVersion languageVersion() {
        return LanguageVersion.JAVA_1_5;
    }
}
