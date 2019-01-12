package OOP.Solution;

import OOP.Provided.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OOPUnitCore {
    //=========== private fields so we can access them from every method without having to carry it around ==========//
    private static Object var;
    private static Object var_copy;
    private static boolean copy_valid;
    private static Class<?> fieldForTestClass;
    private static OOPExpectedExceptionImpl expectedException;
    private static Field exceptionField;
    //================================== comparator for getting the order we want= ===================================//
    private static class compareByOrder implements Comparator<Method>{
        private int getMethodOrder(Method m){
            int order = 0;
            // get the class which declared the method,
            // or the lowest in the inheritance tree which overrides the method
            Class<?> c1 = m.getDeclaringClass();
            if (c1.isAnnotationPresent(OOPTestClass.class)) {
                if (c1.getAnnotation(OOPTestClass.class).value() == OOPTestClass.OOPTestClassType.ORDERED)
                    order = m.getAnnotation(OOPTest.class).order();
            }
            // we assume that if the declaring class is NOT annotated with @OOPTestClass, and the method is still
            // annotated with @OOPTest, then it's order is 0.
            return order;
        }

        @Override
        public int compare(Method m1, Method m2) {
            assert (m1.isAnnotationPresent(OOPTest.class) && m2.isAnnotationPresent(OOPTest.class));
            return getMethodOrder(m1) - getMethodOrder(m2);
        }
    }
    //======================= methods for saving the class instance and recovering it ================================//
    private static Method findCloneMethod(Class<?> c){
        Class<?> itr = c;
        // return clone function (even if it's not public) or null in case it was found in Object and not earlier.
        while(!itr.equals(Object.class) && !itr.isInterface()){
            try{
                return itr.getDeclaredMethod("clone");
            }catch(Exception e){
                // didn't find clone(), try again
                itr = itr.getSuperclass();
            }
        }
        return null;
    }

    private static void copyReflectedObjects(Object source, Object destination){
        Arrays.stream(fieldForTestClass.getDeclaredFields())
                .forEach(field -> {
                    try{
                        if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                            return; // do not copy a static field
                        // to save private fields
                        field.setAccessible(true);
                        try{
                                // try to use clone (even if it's not public or not existing)
                                Method clone_method = findCloneMethod(field.getType());
                                clone_method.setAccessible(true);
                                field.set(destination, clone_method.invoke(field.get(source)));
                        }catch(Exception e1){
                            try {
                                // try to use the copy constructor
                                Constructor copy_ctr = field.getType().getDeclaredConstructor(field.getType());
                                // in case copy constructor is private
                                copy_ctr.setAccessible(true);
                                field.set(destination, copy_ctr.newInstance(field.get(source)));
                            }catch (Exception e2){
                                // field doesn't have either
                                field.set(destination, field.get(source));
                            }
                        }
                    }
                    catch (Exception e3){
                        // not supposed to get here
                        throw new RuntimeException(e3);
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

    //===================== methods for working with reflected  methods and it's annotations =========================//
    private static boolean methodsHaveSameSignature(Method m1, Method m2){
        // for checking if one method overrides the other
        return ( m1.getName().equals(m2.getName())
                && m1.getReturnType().equals(m2.getReturnType())
                && Arrays.equals(m1.getParameterTypes(),m2.getParameterTypes())
        );
    }
    private static Method getLatestVersionForMethod(Method m, Class<?> c) {
        // in case a class overrides an annotated method and doesn't annotate it again
        Class<?> itr = c;
        while (!itr.equals(Object.class) && !itr.isInterface()) {
            try {
                return Arrays.stream(itr.getDeclaredMethods())
                        .filter(m2 -> (m2.equals(m) || methodsHaveSameSignature(m, m2)))
                        .findAny().get();
                // found the method (there must be at most 1 such method)
                // otherwise, get() would throw an exception
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
        // this way we make sure that we get the methods in the desired order
        while(!itr.equals(Object.class) && !itr.isInterface()){
            Collection<Method> methods_to_add;
            LinkedList<Method> tmp = (LinkedList<Method>) annotated_methods.clone();
            // we assume that if the class <itr> is NOT annotated with @OOPTestClass,
            // we still need to add it's annotated methods
            methods_to_add = Arrays.stream(itr.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(annotation))
                    .filter(m -> (tmp.stream() // there is no method with the same signature in the collection already
                            .noneMatch(m2 -> methodsHaveSameSignature(m, m2))))
                    .collect(Collectors.toList());
            annotated_methods.addAll(methods_to_add);
            itr = itr.getSuperclass(); //iterate threw the inheritance tree
        }
        // get the most recent version of the methods (in case they were annotated earlier in the inheritance tree
        // but not in the current class)
        LinkedList<Method> requested_methods = annotated_methods.stream()
                    .map(m -> getLatestVersionForMethod(m, testClass))
                    .collect(Collectors.toCollection(LinkedList::new));
        if(!(annotation.equals(OOPAfter.class) || annotation.equals(OOPTest.class)))
            // the annotation is @OOPBefore or @OOPSetUp, and the order has to be reversed
            Collections.reverse(requested_methods);
        return requested_methods;
    }
    private static List<Method> getSetUpMethods( Class<?> testClass){
        return getMethodsAnnotatedBy(testClass, OOPSetup.class);
    }
    private static List<Method> getTaggedMethods(Class<?> testClass, String tag){
        return getOOPTestMethods(testClass).stream()
                // we assume that the tag must be EXACTLY the same tag
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
            throw new RuntimeException(e);
        }
    }
    private static OOPResultImpl invokeMethod(Method method){
        try{
            method.setAccessible(true);
            method.invoke(var);
            if(expectedException.expectesAnException()){
                // we expect an exception, but no exception was thrown
                return new OOPResultImpl(OOPResult.OOPTestResult.ERROR,
                        expectedException.getExpectedException().getName());
            }
            // method was invoked successfully
            return new OOPResultImpl(OOPResult.OOPTestResult.SUCCESS,null);
        }
        catch (java.lang.reflect.InvocationTargetException e){
            if(e.getTargetException() instanceof OOPAssertionFailure)
                // received an assertion - test failed
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
            methodsToRun.forEach(OOPUnitCore::invokeCheckIntoUnchecked);
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
            getSetUpMethods(testClass).forEach(OOPUnitCore::invokeCheckIntoUnchecked);
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
                // one of the objects is null and the other is not
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
