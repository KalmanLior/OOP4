package OOP.Solution;

import OOP.Provided.OOPAssertionFailure;
import OOP.Provided.OOPExceptionMismatchError;
import OOP.Provided.OOPExpectedException;
import OOP.Provided.OOPResult;


import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
    Note: getDeclaredFields() returns an array of all fields declared by this class or interface which it represents,
    of all access levels, but not the inherited fields!
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
            int order1 = 0, order2 = 0;
            assert (o1.isAnnotationPresent(OOPTest.class) || o2.isAnnotationPresent(OOPTest.class));
            Class<?> c1 = o1.getDeclaringClass();
            if (c1.isAnnotationPresent(OOPTestClass.class)) {
                if (c1.getAnnotation(OOPTestClass.class).value() == OOPTestClass.OOPTestClassType.ORDERED)
                    order1 = o1.getAnnotation(OOPTest.class).order();
            }
            Class<?> c2 = o2.getDeclaringClass();
            if (c2.isAnnotationPresent(OOPTestClass.class)) {
                if (c2.getAnnotation(OOPTestClass.class).value() == OOPTestClass.OOPTestClassType.ORDERED)
                    order2 = o2.getAnnotation(OOPTest.class).order();
            }
            return order1 - order2;
        }
    }
    private static boolean methodsHaveSameSignature(Method m1, Method m2){
        return ( m1.getName().equals(m2.getName())
                && m1.getReturnType().equals(m2.getReturnType())
                && Arrays.equals(m1.getParameterTypes(),m2.getParameterTypes())
                );
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
                                    field.getType().getDeclaredMethod("clone").invoke(field.get(source)));
                        }else{
                            try {
                                // try to use the copy constructor
                                field.set(destination,
                                        field.getType().getDeclaredConstructor((field.getType())).newInstance(field.get(source)));
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
        assert (!copy_valid);
        copyReflectedObjects(var, var_copy);
        copy_valid = true;
    }
    private static void recoverObject(){
        assert (copy_valid);
        copyReflectedObjects(var_copy, var);
        copy_valid = false;
    }

    private static Method getLatestVersionForMethod(Method m, Class<?> c) {
        Class<?> itr = c;
        while (!itr.equals(Object.class) && !itr.isInterface()) {
            try {
                return Arrays.stream(itr.getDeclaredMethods())
                        .filter(m2 -> (m2.equals(m) || methodsHaveSameSignature(m, m2)))
                        .findAny().get();
                // found the method (there must be at most 1 such method)
                // otherwise, get would throw an exception
            } catch (Throwable e) {
                itr = itr.getSuperclass();
            }
        }
        return m; //not supposed to get here
    }
    // methods for getting the desired methods in each phase of the test
    private static List<Method> getMethodsAnnotatedBy(Class<?> testClass, Class<? extends Annotation> annotation){
        LinkedList<Method> annotated_methods = new LinkedList<>();
        Class<?> itr = testClass;
        while(!itr.equals(Object.class) && !itr.isInterface()){
            LinkedList<Method> tmp = (LinkedList<Method>)annotated_methods.clone();
            annotated_methods.addAll(Arrays.stream(itr.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(annotation))
                    .filter(m -> (tmp.stream().filter(m2 ->methodsHaveSameSignature(m, m2))
                            .count() == 0))
                    .collect(Collectors.toList()));
            itr = itr.getSuperclass();
        }
        LinkedList<Method> requested_methods = annotated_methods.stream()
                    .map(m -> getLatestVersionForMethod(m, testClass))
                    .collect(Collectors.toCollection(LinkedList::new));
        if(!(annotation.equals(OOPAfter.class) || annotation.equals(OOPTest.class)))
            Collections.reverse(requested_methods);
        return requested_methods;
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

    private static List<Method> getOOPBeforeMethods(Class<?> testClass, String methodName){
        return getMethodsAnnotatedBy(testClass, OOPBefore.class).stream()
                .filter(m -> Arrays.stream(m.getAnnotation(OOPBefore.class).value())
                        .collect(Collectors.toSet()).contains(methodName))
                .collect(Collectors.toList());
    }
    private static List<Method> getOOPAfterMethods(Class<?> testClass, String methodName){
        return getMethodsAnnotatedBy(testClass, OOPAfter.class).stream()
                .filter(m -> Arrays.stream(m.getAnnotation(OOPAfter.class).value())
                        .collect(Collectors.toSet()).contains(methodName))
                .collect(Collectors.toList());
    }
    // methods for invoking the desired methods
    private static void invokeCheckIntoUnchecked(Method m){
        try{
            m.setAccessible(true);      //for invoking a private method
            m.invoke(var);
        }
        catch (Exception e){
            // we invoked a private method?
            throw new RuntimeException(e);
        }
    }
    private static OOPResultImpl invokeMethod(Method method){
        try{
            method.setAccessible(true);
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
        }
        catch (Exception e){
            throw new RuntimeException(e);
        }
    }
    private static OOPTestSummary runTestsForRunClass(Class<?> testClass, List<Method> testsToRun){
        fieldForTestClass = testClass;
        copy_valid = false;
        Map<String, OOPResult> testMap = new HashMap<>();

        try{
            // create a new instance of the class
            // it is "DeclaredConstructor" in order to get the latest version of it
            Constructor<?> ctr = testClass.getDeclaredConstructor();
            ctr.setAccessible(true);
            var = ctr.newInstance();
            var_copy = ctr.newInstance();
            // get the expected exception field
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
