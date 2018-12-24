package OOP.Solution;

import OOP.Provided.OOPExpectedException;

public class OOPExpectedExceptionImpl implements OOPExpectedException{

    public Class<? extends Exception> getExpectedException(){

    }

    public OOPExpectedException expect(Class<? extends Exception> expected){

    }

    public OOPExpectedException expectMessage(String msg){

    }

    public boolean assertExpected(Exception e){

    }

    static OOPExpectedException none() {

        return OOPExpectedExceptionImpl.none();
    }

}
