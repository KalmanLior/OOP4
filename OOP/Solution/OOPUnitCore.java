package OOP.Solution;

import OOP.Provided.OOPAssertionFailure;
import OOP.Provided.OOPExceptionMismatchError;
import OOP.Provided.OOPExpectedException;
import OOP.Provided.OOPResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
    Note: getDeclaredFields() returns an array of all fields declared by this class or interface which it represents,
    of all access levels, but not the inherited fields!
    getFields() returns an array of only public fields! (not sure about inheritence)
 */

public class OOPUnitCore {
    // private fields so we can access them from every method without having to carry it around
    private static Object var;
    private static Object var_copy;
    private static boolean copy_valid;
    private static Class<?> fieldForTestClass;
    private static OOPExpectedExceptionImpl expectedException;
    private static Field exceptionField;
    // comparator for getting the order we want, might need to be changed
    // in order to support comparing with inherited methods
    private static class compareByOrder implements Comparator<Method>{
        @Override
        public int compare(Method o1, Method o2) {
            int order1, order2;
            try{
                if( !o1.isAnnotationPresent(OOPTest.class) || !o2.isAnnotationPresent(OOPTest.class))
                    throw new Exception();
                order1 = o1.getAnnotation(OOPTest.class).order();
                order2 = o2.getAnnotation(OOPTest.class).order();
            }catch (Exception e){
                //TODO is this the correct way to handle this?
                throw new AssertionError();
            }
            return order1 - order2;
        }
    }
    // methods for saving the class instance and recovering in case of failures
    // in beforeTest and afterTest
    private static void copyReflectedObjects(Object source, Object destination){
        Arrays.stream(fieldForTestClass.getDeclaredFields())
                .forEach(field -> {
                    try{
                        field.setAccessible(true);
                        if(Arrays.stream(field.getType().getInterfaces())
                                .collect(Collectors.toSet())
                                .contains(Cloneable.class)){
                            //field is cloneable
                            field.set(destination,
                                    field.getType().getMethod("clone").invoke(field.get(source)));
                        }else{
                            try {
                                // try to use the copy constructor
                                field.set(destination,
                                        field.getType().getConstructor((field.getType())).newInstance(field.get(source)));
                            }catch (Exception e){
                                // field doesn't have either
                                field.set(destination, field.get(source));
                            }
                        }
                    }
                    catch (Exception e){
                        // something weird happened, not supposed to get here
                    }
                } );
    }
    private static void snapshotObject(){
        if(copy_valid){
            //something wierd happened, not supposed to get here
            System.out.println("Why am i here in snapshot object???");
        }
        copyReflectedObjects(var, var_copy);
        copy_valid = true;
    }
    private static void recoverObject(){
        if(!copy_valid){
            //something wierd happened, not supposed to get here
        }
        copyReflectedObjects(var_copy, var);
        copy_valid = false;
    }
    // methods for getting the desired methods in each phase of the test
    private static List<Method> getMethodsAnnotatedBy(Class<?> testClass, Class<? extends Annotation> annotation){
        LinkedList<Method> annotated_methods = new LinkedList<>();
        Class<?> itr = testClass;
        while(!itr.equals(Object.class)){
            LinkedList<Method> tmp = (LinkedList<Method>)annotated_methods.clone();
            Arrays.stream(itr.getDeclaredMethods()).forEach(m -> m.setAccessible(true));
            annotated_methods.addAll(Arrays.stream(itr.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(annotation))
                    .filter(m -> (tmp.stream().filter(m2 ->
                            (m2.getName().equals(m.getName()) &&
                            m2.getReturnType().equals(m.getReturnType()) &&
                                    Arrays.equals(m2.getParameterTypes(), m.getParameterTypes()))).count() == 0))
                    .collect(Collectors.toList()));
            itr = itr.getSuperclass();
        }
        annotated_methods.forEach(m -> m.setAccessible(true));
        if(!(annotation.equals(OOPAfter.class) || annotation.equals(OOPTest.class)))
            Collections.reverse(annotated_methods);
        return annotated_methods;
    }
    private static List<Method> getSetUpMethods( Class<?> testClass){
        return getMethodsAnnotatedBy(testClass, OOPSetup.class);
    }
    private static List<Method> getTaggedMethods(Class<?> testClass, String tag){
        return getOOPTestMethods(testClass).stream()
                .filter(m -> m.getAnnotation(OOPTest.class).tag().equals(tag))
                .collect(Collectors.toList());
    }
    private static List<Method> getOOPTestMethods(Class<?> testClass){
        return getMethodsAnnotatedBy(testClass, OOPTest.class);
    }
    private static List<Method> getMethodsBeforeOrAfter(Class<?> testClass, String methodName, boolean before){
       /* return Arrays.stream(testClass.getMethods())
                .filter(m -> {
                    Stream<String> tmpStream;
                    if (before){
                        if(!m.isAnnotationPresent(OOPBefore.class)){
                            return false;
                        }
                        tmpStream = Arrays.stream(m.getAnnotation(OOPBefore.class).value());
                    }else{
                        if(!m.isAnnotationPresent(OOPAfter.class)){
                            return false;
                        }
                        tmpStream = Arrays.stream(m.getAnnotation(OOPAfter.class).value());
                    }
                    return tmpStream
                            .collect(Collectors.toSet())
                            .contains(methodName);
                })
                .collect(Collectors.toList());*/

        if(before)
            return getMethodsAnnotatedBy(testClass, OOPBefore.class).stream()
                    .filter(m -> Arrays.stream(m.getAnnotation(OOPBefore.class).value())
                            .collect(Collectors.toSet()).contains(methodName))
                    .collect(Collectors.toList());
        return getMethodsAnnotatedBy(testClass, OOPAfter.class).stream()
                .filter(m -> Arrays.stream(m.getAnnotation(OOPAfter.class).value())
                        .collect(Collectors.toSet()).contains(methodName))
                .collect(Collectors.toList());

    }
    private static List<Method> getOOPBeforeMethods(Class<?> testClass, String methodName){
        return getMethodsBeforeOrAfter(testClass, methodName, true);
    }
    private static List<Method> getOOPAfterMethods(Class<?> testClass, String methodName){
        return getMethodsBeforeOrAfter(testClass, methodName, false);
    }
    // methods for invoking the desired methods
    private static void invokeCheckIntoUnchecked(Method m){
        try{
            //TODO what if we are trying to invoke a private method? -> m.setAccessible(true)
            m.invoke(var);
        }
        catch (Exception e){
            // we invoked a private method
            throw new RuntimeException(e);
        }
    }
    private static OOPResultImpl invokeMethod(Method method){
        try{
            method.invoke(var);
            if(expectedException.expectesAnException()){
                // we expect an exception, but no exception was thrown
                return new OOPResultImpl(OOPResult.OOPTestResult.ERROR,expectedException.getExpectedException().getClass().getName());
            }
            // method was invoked successfully
            return new OOPResultImpl(OOPResult.OOPTestResult.SUCCESS,null);
        }
        catch (java.lang.reflect.InvocationTargetException e){
            // received an assertion - test failed
            if(e.getTargetException().getClass().equals(OOPAssertionFailure.class))
                return new OOPResultImpl(OOPResult.OOPTestResult.FAILURE, e.getMessage());
            if(!expectedException.expectesAnException())
                // we did not expect an exception, but an exception was thrown
                return new OOPResultImpl(OOPResult.OOPTestResult.ERROR,e.getClass().getName());
            if(expectedException.assertExpected((Exception) e.getTargetException()))
                // the expected exception was thrown
                return new OOPResultImpl(OOPResult.OOPTestResult.SUCCESS,null);
            // an exception which we did not expect was thrown
            return new OOPResultImpl(OOPResult.OOPTestResult.EXPECTED_EXCEPTION_MISMATCH,
                    (new OOPExceptionMismatchError(expectedException.getExpectedException(), e.getClass() )).getMessage());
        }
        catch (Exception e){
            // we get here if we try to invoke a private method
            throw new RuntimeException(e);
        }
    }
    private static boolean runBeforeOrAfterTests(Method method, Function<String, List<Method>> action){
        List<Method> methodsToRun = action.apply(method.getName());
        snapshotObject();
        try {
            methodsToRun.forEach(m -> invokeCheckIntoUnchecked(m));
        }catch (Exception e){
            recoverObject();
            // we are not supposed to get an exception in recoverObject, because it does the same
            // as snapshotObject, and if we are here it means that snapshot succeeded
            return false;
        }
        copy_valid = false;
        return true;
    }
    private static OOPResultImpl invokeTestMethod(Method method) {
        OOPResultImpl tmp;
        try{
            // run OOPBefore methods
            if(!runBeforeOrAfterTests(method, str -> getOOPBeforeMethods(fieldForTestClass, str))){
                return new OOPResultImpl(OOPResult.OOPTestResult.ERROR, fieldForTestClass.getName());
            }
            // TODO should the expected exception be reset here, or before the BeforeMethods?
            if(exceptionField != null) {
                exceptionField.set(var, OOPExpectedException.none());
                expectedException = (OOPExpectedExceptionImpl) exceptionField.get(var);
            }
            // run test
             tmp = invokeMethod(method);

            // run OOPAfter methods
            if(!runBeforeOrAfterTests(method, str -> getOOPAfterMethods(fieldForTestClass, str))){
                return new OOPResultImpl(OOPResult.OOPTestResult.ERROR, fieldForTestClass.getName());
            }
            return tmp;

        }catch (Exception e){
            // we got an exception in getOOPBeforeMethods or getOOPAfterMethods
            // most likely in getOOPBefore, need to think about the situations
            System.out.println("why am i here???");
        }
        //TODO: is this ok? if we got here, we didn;t catch anything
        return new OOPResultImpl(OOPResult.OOPTestResult.SUCCESS, null);
    }
    private static OOPTestSummary runTestsForRunClass(Class<?> testClass, List<Method> testsToRun){
        fieldForTestClass = testClass;
        copy_valid = false;
        Map<String, OOPResult> testMap = new HashMap<>();

        try{
            // create a new instance of the class
            // it is "DeclaredConstructor" in order to get the latest version of it
            Arrays.stream(testClass.getNestHost().getDeclaredFields()).forEach(c -> c.setAccessible(true));
            Arrays.stream(testClass.getNestHost().getDeclaredMethods()).forEach(c -> c.setAccessible(true));
            Arrays.stream(testClass.getDeclaredFields()).forEach(c -> c.setAccessible(true));
            Arrays.stream(testClass.getDeclaredMethods()).forEach(c -> c.setAccessible(true));
            testClass.getDeclaredConstructor().setAccessible(true);
            var = testClass.getDeclaredConstructor().newInstance();
            // get the expected exception field
            // TODO: fix it to search reqursively in the inheritence tree for the field
            exceptionField = Arrays.stream(testClass.getDeclaredFields())
                    .filter(field ->  field.isAnnotationPresent(OOPExceptionRule.class))
                    .findFirst().get();
            exceptionField.setAccessible(true);    //For private fields
            expectedException =  (OOPExpectedExceptionImpl) exceptionField.get(var);
        }catch (Exception e){
            // we are given that we may assume that the class wll have a constructor,
            // so we are here because there is no OOPExceptionRule field in the testClass
            exceptionField = null;
            expectedException =(OOPExpectedExceptionImpl) OOPExpectedExceptionImpl.none();
        }
        finally {
            // run setUp methods
            // TODO improve it to work according to the order of inheritence
            getSetUpMethods(testClass).forEach(m -> invokeCheckIntoUnchecked(m) );
            // run the tests
            if(testClass.getAnnotation(OOPTestClass.class).value()
                       == OOPTestClass.OOPTestClassType.ORDERED)
                testsToRun.sort(new compareByOrder());
            testsToRun.forEach(m -> {
                testMap.put(m.getName(), invokeTestMethod(m));
            });
        }
        return new OOPTestSummary(testMap);
    }

    // the methods of part 3
    public static void assertEquals(Object expected,  Object actual){
        if(!(expected == null && actual == null)) {
            boolean res = false;
            try {
                // in case equals throws an exception
                res = !(expected.equals(actual) && actual.equals(expected));
            } catch (Exception e) {
                throw new OOPAssertionFailure(expected, actual);
            }
            if (res)
                throw new OOPAssertionFailure(expected, actual);
        }
        // both are null, which is ok
    }
    public static void fail(){
        throw new OOPAssertionFailure();
    }
    public static OOPTestSummary runClass(Class<?> testClass){
        if(testClass == null || !testClass.isAnnotationPresent(OOPTestClass.class))
            throw new IllegalArgumentException();
        List<Method> testToRun = getOOPTestMethods(testClass);
        return runTestsForRunClass(testClass, testToRun);
    }
    public static  OOPTestSummary runClass(Class<?> testClass, String tag){
        if(testClass == null || tag == null || !testClass.isAnnotationPresent(OOPTestClass.class))
            throw new IllegalArgumentException();
        List<Method> testsToRun = getTaggedMethods(testClass, tag);
        return runTestsForRunClass(testClass, testsToRun);
    }
}
