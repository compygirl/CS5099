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

import savilerow.*;
import java.util.*;
import java.io.*;
import savilerow.treetransformer.*;
import savilerow.model.Sat;

/* =============================================================================

Class to contain an ASTNode and a variable Identifier (or a constant)

Represents reification to the variable if the ASTNode is a relation.
If the ASTNode is arithmetic, it represents equality with the variable.

For example, ToVariable(x+y+z, aux1)   represents the constraint 
x+y+z=aux1

and ToVariable(AllDifferent(blah), aux1) represents
reify(alldiff(blah), aux1)

May also contain !aux1 as reification variable. 

==============================================================================*/

public class ToVariable extends ASTNode
{
    public static final long serialVersionUID = 1L;
    public ToVariable(ASTNode constraint, ASTNode var)
    {
        super(constraint, var);
    }
    
    public ASTNode copy()
    {
        return new ToVariable(getChild(0), getChild(1));
    }
    
    public boolean isRelation() {return true; }
    
    public String toString() {
        if(getChild(0).isRelation()) {
            return "("+getChild(0)+"<-tv>"+getChild(1)+")";
        }
        else {
            return "("+getChild(0)+"=tv="+getChild(1)+")";
        }
    }
    
    // Similar simplify method to Equals.
    public ASTNode simplify() {
        // Special case to protect Reify -- if the first child is a constant or a single variable, turn it into iff.
        if(getChild(0) instanceof BooleanConstant || (getChild(0) instanceof Identifier && getChild(0).isRelation())) {
            return new Iff(getChild(0), getChild(1));
        }
        
        if(getChild(0) instanceof NumberConstant || (getChild(0) instanceof Identifier && getChild(0).isNumerical())) {
            return new Equals(getChild(0), getChild(1));
        }
	    
	    if(getChild(0).equals(getChild(1))) {  // If symbolically equal, return true.
	        return new BooleanConstant(true);
	    }
	    if(getChild(0).isConstant() && getChild(1).isConstant()) {
	        // If equal when interpreted as integer.... (includes 1=true)
	        return new BooleanConstant( getChild(0).getValue() == getChild(1).getValue() );
	    }
	    
	    Intpair b0=getChild(0).getBounds();
	    Intpair b1=getChild(1).getBounds();
	    
	    if(b0.lower>b1.upper) {
	        return new BooleanConstant(false);  // lower bound of c1 is greater than upper bound of c2.
	    }
	    if(b0.upper<b1.lower) {
	        return new BooleanConstant(false);  // upper bound of c1 is less than lower bound of c2.
	    }
	    return this;
	}
    
	//  If contained in a Negate, push the negation inside 
	@Override
	public boolean isNegatable() {
	    return getChild(1).isRelation();
	}
	@Override
	public ASTNode negation() {
	    return new ToVariable(getChild(0), new Negate(getChild(1)));
	}
	
    public void toMinion(StringBuffer b, boolean bool_context)
    {
        assert bool_context;
        if(getChild(0).isRelation() && !(getChild(0) instanceof SafeElement) && !(getChild(0) instanceof Element) )
        {
            b.append("reify(");
            getChild(0).toMinion(b, true);
            b.append(", ");
            getChild(1).toMinion(b, false);
            b.append(")");
        }
        else
        {
            getChild(0).toMinionWithAuxVar(b, getChild(1));
        }
    }
    
    public void toDominionInner(StringBuffer b, boolean bool_context)
    {
        if(getChild(0).isRelation())
        {
            b.append(CmdFlags.getCtName()+" ");
            b.append("reify(");
            getChild(0).toDominion(b, true);
            b.append(", ");
            getChild(1).toDominion(b, false);
            b.append(")");
        }
        else
        {
            getChild(0).toDominionWithAuxVar(b, getChild(1));
        }
    }
    
    public void toFlatzinc(StringBuffer b, boolean bool_context) {
        if(getChild(0).isRelation()) {
            assert ! (getChild(0) instanceof ToVariable);
            // Some constraints have a non-standard name without the _reif at the end
            if(getChild(0) instanceof And || getChild(0) instanceof Or) {
                getChild(0).toFlatzincWithAuxVar(b, getChild(1));
            }
            else {
                // Assume standard naming. i.e. c_reif is the reified version of c.
                // Get the unreified constraint into temp
                StringBuffer temp=new StringBuffer();
                getChild(0).toFlatzinc(temp, true);
                
                StringBuffer tempaux=new StringBuffer();
                getChild(1).toFlatzinc(tempaux, true);
                
                // replace ); with  ,tempaux);
                int t1=temp.lastIndexOf(");");
                
                temp.replace(t1, t1+2, ","+tempaux.toString()+");");
                
                // replace ( with _reif(
                int t2=temp.indexOf("(");
                temp.replace(t2, t2+1, "_reif(");
                
                b.append(temp);
            }
        }
        else {
            // Numerical
            getChild(0).toFlatzincWithAuxVar(b, getChild(1));
        }
    }
    
    public void toMinizinc(StringBuffer b, boolean bool_context) {
        b.append("( ");
        if(getChild(0).isRelation()) {
            getChild(0).toMinizinc(b, true);
            b.append(" <-> ");
            getChild(1).toMinizinc(b, true);
        }
        else {
            getChild(0).toMinizinc(b, false);
            b.append(" == ");
            getChild(1).toMinizinc(b, false);
        }
        b.append(" )");
    }
    
    //  Similar to Equals and Iff.
    public Long toSATLiteral(Sat satModel) {
	    if(getChild(0).isConstant()) {
	        return getChild(1).directEncode(satModel, getChild(0).getValue());
        }
        if(getChild(1).isConstant()) {
            if(getChild(0) instanceof SATLiteral || getChild(0) instanceof Identifier) {
                return getChild(0).directEncode(satModel, getChild(1).getValue());
            }
        }
        return null;
	}
	
    public void toSAT(Sat satModel) throws IOException
    {
        if(getChild(0).isRelation() && !(getChild(0) instanceof SafeElement) && !(getChild(0) instanceof Element) )
        {
            if (getChild(1) instanceof BooleanConstant) {
    		    if(getChild(1).getValue()==1){
                    getChild(0).toSAT(satModel);
                }
                else {
                    assert getChild(1).getValue()==0;
                    new Negate(getChild(0)).toSAT(satModel);
                }
            }
            else {
                // Child 1 must be a SATLiteral. A negation would already have been applied.
                long satLit;
                if(getChild(1) instanceof SATLiteral) {
                    satLit=((SATLiteral) getChild(1)).getLit();
                }
                else {
                    if(! getChild(1).isRelation()) {
                        System.err.println("Weird expression in output: "+this);
                    }
                    satLit=((Identifier) getChild(1)).directEncode(satModel, 1);
                }
                
                getChild(0).toSATWithAuxVar(satModel, satLit);
            }
        }
        else
        {
            // Call the method that takes an ASTNode.
            getChild(0).toSATWithAuxVar(satModel, getChild(1));
        }
    }
}

