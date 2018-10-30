package savilerow.expression;
/*

    Savile Row http://savilerow.cs.st-andrews.ac.uk/
    Copyright (C) 2014, 2015 Peter Nightingale
    
    This file is part of Savile Row.
    
    Savile Row is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    Savile Row is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    
    You should have received a copy of the GNU General Public License
    along with Savile Row.  If not, see <http://www.gnu.org/licenses/>.

*/

import savilerow.model.SymbolTable;
import savilerow.*;
import savilerow.model.*;

import java.util.*;
import java.io.*;

//  This class represents SAT literals that are introduced soon before outputting
//  SAT.  Allows (for example) x=5 or x!=5 to be replaced with one of these to avoid encoding
//  a constraint.

public class SATLiteral extends ASTNode {
    public static final long serialVersionUID = 1L;
    private long lit;
    private Sat satModel;
    
    public SATLiteral(long _lit, Sat _satModel) {
        super();
        lit=_lit;
        satModel=_satModel;
    }
    
    public String toString() {
        return "SATLiteral("+String.valueOf(lit)+")";
    }
    
    public long getLit(){
        return lit;
    }
    
    public ASTNode copy() {
        return new SATLiteral(lit, satModel);
    }
    
    @Override
    public boolean equals(Object other) {
        if (! (other instanceof SATLiteral)) {
            return false;
        }
        return ((SATLiteral) other).lit==lit;
    }
    @Override
    public int hashCode() {
        return ((Long)lit).hashCode();
    }
    
    // Is it a bool or matrix of bool.
    public boolean isRelation() {
        return true;
    }
    public boolean isNumerical() {
        return false;
    }
    public boolean isSet() {
        return false;
    }
    public int getCategory() {
        return ASTNode.Decision;
    }
    
    public int getDimension() {
        return 0;
    }
    @Override
    public ASTNode simplify() {
        // Allow other simplifiers to do their work by turning this into true or false when possible 
        if(lit==satModel.getTrue()) return new BooleanConstant(true);
        if(lit==-satModel.getTrue()) return new BooleanConstant(false);
        return this;
    }
    
    // Return literal for this==value
    public long directEncode(Sat satModel, long value) {
        if(value==0) {
            return -lit;
        }
        else if(value==1) {
            return lit;
        }
        else {
            return -satModel.getTrue();  // Default value is false. 
        }
    }
    public long orderEncode(Sat satModel, long value) {
        if(value==0) {
            return -lit;
        }
        else if(value>=1) {
            return satModel.getTrue();
        }
        else {
            // value <0
            return -satModel.getTrue();
        }
    }
    
    @Override
	public boolean isNegatable() {
	    return true;
	}
	@Override
	public ASTNode negation() {
	    return new SATLiteral(-lit, satModel);
	}
    
    public void toSAT(Sat satModel) throws IOException {
        //System.out.println("WARNING: SAT literal "+lit+" is always true");
        satModel.addClause(String.valueOf(lit));
    }
    public void toSATWithAuxVar(Sat satModel, long auxVar) throws IOException {
        //System.out.println("WARNING: Inefficient duplication of a SAT variable in SATLiteral "+this+" contained in expression: "+getParent());
        satModel.addClause((-auxVar)+" "+lit);
        satModel.addClause(auxVar+" "+(-lit));
    }
}
