package OOP.Solution;

import OOP.Provided.OOPExpectedException;
import java.util.HashSet;
import java.util.stream.Collectors;

public class OOPExpectedExceptionImpl implements OOPExpectedException{
    private Class<? extends Exception> expectedException;
    private HashSet<String> expectedMessages;
    private boolean is_none;
    private OOPExpectedExceptionImpl(){
        expectedMessages = new HashSet<>();
        is_none = true;
    }
    public Class<? extends Exception> getExpectedException(){
        return expectedException;
    }

    public OOPExpectedException expect(Class<? extends Exception> expected){
        is_none = false;
        expectedException = expected;
        return this;
    }

    public OOPExpectedException expectMessage(String msg){
        is_none = false;
        expectedMessages.add(msg);
        return this;
    }

    public boolean assertExpected(Exception e){
        // check that we expect an exception
        if(is_none)
            return false;
        // check that the given exception is an instance of the expectedException
        if (!e.getClass().isInstance(expectedException))
            return false;
        String msg = e.getMessage();
        // check that all expectedMessages are substrings of the message of e
        return expectedMessages.stream()
                .filter(s -> !(msg.contains(s)))
                .collect(Collectors.toSet())
                .isEmpty();
    }

    public static OOPExpectedException none() {
        return new OOPExpectedExceptionImpl();
    }

}
