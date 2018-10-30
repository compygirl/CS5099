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
import savilerow.model.SymbolTable;
import savilerow.treetransformer.*;
import savilerow.model.Sat;

// Equality between two variables or a variable and a constant (two constants would be simplified to true or false).
// Cannot represent reification or ternary numerical constraint -- see ToVariable. 

public class Equals extends BinOp
{
    public static final long serialVersionUID = 1L;
	public Equals(ASTNode l, ASTNode r) {
		super(l, r);
	}
	
	public ASTNode copy() {
	    return new Equals(getChild(0), getChild(1));
	}
	public boolean isRelation(){return true;}
	
	public ASTNode simplify() {
	    if(getChild(0).equals(getChild(1))) {  // If symbolically equal, return true.
	        return new BooleanConstant(true);
	    }
	    
        // NOTE: This is copied from Iff, which has the exact same problem.
        // The two may be unified somehow, but we duplicate the check for the moment.
        // If one side is the negation of the other, return false.  
        // Prevents deletevars unifying  x=not(x) and causing infinite recursion of replacing x with not(x). 
        if(getChild(0) instanceof Negate && getChild(0).getChild(0).equals(getChild(1))) {
            return new BooleanConstant(false);
        }
        if(getChild(1) instanceof Negate && getChild(1).getChild(0).equals(getChild(0))) {
            return new BooleanConstant(false);
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
	    
	    TransformSimplify ts=new TransformSimplify();
	    // If one child is a variable and the other a constant
	    if(getChild(0) instanceof Identifier && getChild(1).isConstant()) {
	        ASTNode domain=((Identifier)getChild(0)).getDomain();
	        if(domain.getCategory() == ASTNode.Constant) {
	            domain=ts.transform(domain);
                if(! domain.containsValue(getChild(1).getValue())) {
                    return new BooleanConstant(false);
                }
	        }
	    }
	    if(getChild(1) instanceof Identifier && getChild(0).isConstant()) {
	        ASTNode domain=((Identifier)getChild(1)).getDomain();
	        if(domain.getCategory() == ASTNode.Constant) {
	            domain=ts.transform(domain);
                if(! domain.containsValue(getChild(0).getValue())) {
                    return new BooleanConstant(false);
                }
	        }
	    }
	    
	    // Now simplify sum1=sum2 to sum1-sum2=0
	    if(getChild(0) instanceof WeightedSum && getChild(1) instanceof WeightedSum) {
	        return new Equals(BinOp.makeBinOp("-", getChild(0), getChild(1)), new NumberConstant(0));
	    }
	    
	    // Finally it helps identical CSE if sums have no constants in them. 
	    // Shift the constant to the other side to combine with constant/param/quantifier id. 
	    if(getChild(0) instanceof WeightedSum && getChild(0).getCategory()==ASTNode.Decision && getChild(1).getCategory()<ASTNode.Decision) {
	        Pair<ASTNode, ASTNode> p1=((WeightedSum)getChild(0)).retrieveConstant();
	        if(p1!=null) {
	            return new Equals(p1.getSecond(), BinOp.makeBinOp("-", getChild(1), p1.getFirst()));
	        }
	    }
	    if(getChild(1) instanceof WeightedSum && getChild(1).getCategory()==ASTNode.Decision && getChild(0).getCategory()<ASTNode.Decision) {
	        Pair<ASTNode, ASTNode> p1=((WeightedSum)getChild(1)).retrieveConstant();
	        if(p1!=null) {
	            return new Equals(BinOp.makeBinOp("-", getChild(0), p1.getFirst()), p1.getSecond());
	        }
	    }
	    
	    return this;
	}
	
	@Override
	public boolean isNegatable() {
	    return true;
	}
	@Override
	public ASTNode negation() {
	    return new AllDifferent(getChild(0), getChild(1));
	}
	
	/*public boolean propagate() {
	    Intpair a0=getChild(0).getBounds();
	    Intpair a1=getChild(1).getBounds();
	    boolean changed=false;
	    
	    if(getChild(0) instanceof Identifier && getChild(0).getCategory()==ASTNode.Decision) {
            Identifier var=((Identifier) getChild(0));
            changed=var.setBounds(a1.lower, a1.upper);
        }
        if(getChild(1) instanceof Identifier && getChild(1).getCategory()==ASTNode.Decision) {
            Identifier var=((Identifier) getChild(1));
            changed=changed || var.setBounds(a0.lower, a0.upper);
        }
        return changed;
	}*/
	
	@Override
	public ASTNode normalise() {
	    if(getChild(0).hashCode()>getChild(1).hashCode()) {
	        return new Equals(getChild(1), getChild(0));
	    }
	    return this;
	}
	
	public String toString() {
	    return "("+getChild(0)+"="+getChild(1)+")";
	}
	public void toMinion(StringBuffer b, boolean bool_context)
	{
	    assert bool_context;
	    if(getChild(0).isConstant()) {
	        if(CmdFlags.getUseBoundVars() && getChild(1).exceedsBoundThreshold() ) {
	            b.append("eq(");
	        }
	        else {
	            b.append("w-literal(");
	        }
	        getChild(1).toMinion(b, false);
	        b.append(",");
	        getChild(0).toMinion(b, false);
	        b.append(")");
	    }
	    else if(getChild(1).isConstant()) {
	        if(CmdFlags.getUseBoundVars() && getChild(0).exceedsBoundThreshold() ) {
	            b.append("eq(");
	        }
	        else {
	            b.append("w-literal(");
	        }
	        getChild(0).toMinion(b, false);
	        b.append(",");
	        getChild(1).toMinion(b, false);
	        b.append(")");
	    }
	    else {
	        if(CmdFlags.getUseBoundVars() && ( getChild(0).exceedsBoundThreshold() || getChild(1).exceedsBoundThreshold() )) {
                b.append("eq(");
            }
            else {
                b.append("gaceq(");
            }
            getChild(0).toMinion(b, false);
            b.append(",");
            getChild(1).toMinion(b, false);
            b.append(")");
	    }
	}
	public void toDominionInner(StringBuffer b, boolean bool_context)
	{
	    // literal propagates better than eq in places where it can be negated (e.g. in a reify)
	    if(getChild(0).getCategory() <= ASTNode.Quantifier) {
	        b.append(CmdFlags.getCtName()+" ");
            b.append("literal(");
            getChild(1).toDominion(b, false);
            b.append(",");
            getChild(0).toDominionParam(b);
            b.append(")");
            return;
	    }
	    if(getChild(1).getCategory() <= ASTNode.Quantifier) {
	        b.append(CmdFlags.getCtName()+" ");
            b.append("literal(");
            getChild(0).toDominion(b, false);
            b.append(",");
            getChild(1).toDominionParam(b);
            b.append(")");
            return;
	    }
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("eq(");
	    getChild(0).toDominion(b, false);
	    b.append(",");
	    getChild(1).toDominion(b, false);
	    b.append(")");
	}
	public void toDominionParam(StringBuffer b) {
	    b.append("(");
	    getChild(0).toDominionParam(b);
	    b.append("=");
	    getChild(1).toDominionParam(b);
	    b.append(")");
	}
	public void toFlatzinc(StringBuffer b, boolean bool_context) {
	    b.append("constraint int_eq(");
	    getChild(0).toFlatzinc(b, false);
	    b.append(",");
	    getChild(1).toFlatzinc(b, false);
	    b.append(");");
	}
	
	// Might be a problem here.. what if it contains a bool type.
	public void toMinizinc(StringBuffer b, boolean bool_context) {
	    b.append("(");
	    getChild(0).toMinizinc(b, false);
	    b.append("==");
	    getChild(1).toMinizinc(b, false);
	    b.append(")");
	}
	
	////////////////////////////////////////////////////////////////////////////
	//  SAT encoding
	
	public Long toSATLiteral(Sat satModel) {
	    if(getChild(0).isConstant()) {
	        return getChild(1).directEncode(satModel, getChild(0).getValue());
        }
        if(getChild(1).isConstant()) {
            return getChild(0).directEncode(satModel, getChild(1).getValue());
        }
        return null;
	}
	
	public void toSAT(Sat satModel) throws IOException {
	    // Support encoding just for equality. 
	    // [x!=a] \/ [y=a] for all a,  and both ways round (x and y). 
	    encodeEquality(satModel, false, 0);
	}
	
	public void toSATWithAuxVar(Sat satModel, long aux) throws IOException {
	    encodeEquality(satModel, true, aux);
	    
	    // Direct encode of the inverse constraint.
	    ArrayList<Intpair> domain1=Sat.getIntervalSetSAT(getChild(0));
        ArrayList<Intpair> domain2=Sat.getIntervalSetSAT(getChild(1));
        
        for (Intpair pair1 : domain1)
        {
            for (long i=pair1.lower; i<=pair1.upper; i++)
            {
                satModel.addClause(String.valueOf(-getChild(0).directEncode(satModel, i))+" "+String.valueOf(-getChild(1).directEncode(satModel, i))+" "+String.valueOf(aux));
            }
        }
	}
	
	
	private void encodeEquality(Sat satModel, boolean auxused, long aux) throws IOException {
	    //  aux ->  var1 = var2
	    ArrayList<Intpair> domain1=Sat.getIntervalSetSAT(getChild(0));
        ArrayList<Intpair> domain2=Sat.getIntervalSetSAT(getChild(1));
        
        for (Intpair pair1 : domain1)
        {
            for (long i=pair1.lower; i<=pair1.upper; i++)
            {
                if(auxused) {
                    satModel.addClause(String.valueOf(-getChild(0).directEncode(satModel, i))+" "+String.valueOf(getChild(1).directEncode(satModel, i))+" "+String.valueOf(-aux));
                }
                else {
                    satModel.addClause(String.valueOf(-getChild(0).directEncode(satModel, i))+" "+String.valueOf(getChild(1).directEncode(satModel, i)));
                }
            }
        }
        
        for (Intpair pair1 : domain2)
        {
            for (long i=pair1.lower; i<=pair1.upper; i++)
            {
                if(auxused) {
                    satModel.addClause(String.valueOf(-getChild(1).directEncode(satModel, i))+" "+String.valueOf(getChild(0).directEncode(satModel, i))+" "+String.valueOf(-aux));
                }
                else {
                    satModel.addClause(String.valueOf(-getChild(1).directEncode(satModel, i))+" "+String.valueOf(getChild(0).directEncode(satModel, i)));
                }
            }
        }
	}
	
    public boolean childrenAreSymmetric() {
        return true;
    }
    
    public boolean canChildBeConvertedToDifference(int childIndex) {
        return isMyOnlyOtherSiblingEqualZero(childIndex);
    }

}