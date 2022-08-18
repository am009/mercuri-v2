package ssa.ds;

import java.util.ArrayList;
import java.util.List;

public class PlaceHolder extends Value {

    @Override
    public String toValueString() {
        return "!!!placeholder";
    }
}
