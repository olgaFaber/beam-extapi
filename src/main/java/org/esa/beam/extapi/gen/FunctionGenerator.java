package org.esa.beam.extapi.gen;

/**
 * @author Norman Fomferra
 */
public interface FunctionGenerator extends CodeGenerator {
    ApiMethod getApiMethod();

    ParameterGenerator[] getParameterGenerators();

    String getFunctionName(GeneratorContext context);

    String generateInitCode(GeneratorContext context);

    String generateFunctionSignature(GeneratorContext context);

    String generateReturnCode(GeneratorContext context);

    String generateDocText(GeneratorContext context);
}
