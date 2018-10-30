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
import savilerow.model.*;
import savilerow.treetransformer.*;

public class InSet extends BinOp
{
    public static final long serialVersionUID = 1L;
    public InSet(ASTNode a, ASTNode set)
	{
	    super(a, set);
	}
	
	public ASTNode copy()
	{
	    return new InSet(getChild(0), getChild(1));
	}
	public boolean isRelation(){return true;}
	
	public ASTNode simplify() {
	    if(getChild(0).isConstant() && getChild(1).getCategory()==ASTNode.Constant) {
	        if(getChild(1).containsValue(getChild(0).getValue())) {
	            return new BooleanConstant(true);
	        }
	        else {
	            return new BooleanConstant(false);
	        }
	    }
	    
	    if(getChild(1).getCategory()==ASTNode.Constant) {
	        if(getChild(1).getIntervalSet().size()==0) {
	            // No values in set. 
	            return new BooleanConstant(false);
	        }
	        
	        if(getChild(0) instanceof Identifier && getChild(0).getCategory()==ASTNode.Decision) {
	            ASTNode dom=((Identifier)getChild(0)).global_symbols.getDomain(getChild(0).toString());
	            
	            ASTNode tmp=new SetDifference(dom, getChild(1));  // subtract the set from the domain and see if there
	            // are any domain values left. If not, the inset is always true. 
	            
	            TransformSimplify ts=new TransformSimplify();
	            tmp=ts.transform(tmp);
	            if(tmp.getIntervalSet().size()==0) {
	                return new BooleanConstant(true);
	            }
	        }
	    }
	    
	    return this;
	}
	
	@Override
	public boolean typecheck(SymbolTable st) {
	    if(!getChild(0).typecheck(st) || !getChild(1).typecheck(st)) {
	        return false;
	    }
	    
	    if(!getChild(1).isSet()) {
	        CmdFlags.println("ERROR: Expected set on right hand side of 'in' operator: "+this);
	        return false;
	    }
	    
	    if(getChild(0).getDimension()>0 || getChild(0).isSet()) {
	        CmdFlags.println("ERROR: Expected integer or boolean expression on left hand side of 'in' operator: "+this);
	        return false;
	    }
	    
        return true;
    }
	
	public String toString() {
	    return "("+getChild(0)+" in "+getChild(1)+")";
	}
	public void toMinion(StringBuffer b, boolean bool_context) {
	    assert bool_context;
	    b.append("w-inintervalset(");
        getChild(0).toMinion(b, false);
        b.append(",[");
        ArrayList<Intpair> a=getChild(1).getIntervalSet();
        for(int i=0; i<a.size(); i++) {
            b.append(a.get(i).lower);
            b.append(",");
            b.append(a.get(i).upper);
            if(i<a.size()-1) b.append(",");
        }
        b.append("])");
	}
	public void toDominionInner(StringBuffer b, boolean bool_context) {
	    b.append("inlist(");
	    getChild(0).toDominion(b, false);
	    b.append(",");
	    b.append(getChild(1).getValueSet().toString());
	    b.append(")");
    }
    public void toDominionParam(StringBuffer b) {
        b.append("Or([");
        ArrayList<ASTNode> setranges=getChild(1).getChild(0).getChildren();
        for(int i=0; i<setranges.size(); i++) {
            ASTNode item=setranges.get(i);
            if(item instanceof Range) {
                b.append("And([(");
                getChild(0).toDominionParam(b);
                b.append(">=");
                item.getChild(0).toDominionParam(b);
                b.append("),(");
                getChild(0).toDominionParam(b);
                b.append("<=");
                item.getChild(1).toDominionParam(b);
                b.append(")])");
            }
            else {
                b.append("(");
                getChild(0).toDominionParam(b);
                b.append("=");
                item.toDominionParam(b);
                b.append(")");
            }
            if(i<setranges.size()-1) b.append(",");
        }
        b.append("])");
    }
    
	public void toFlatzinc(StringBuffer b, boolean bool_context) {
	    b.append("constraint set_in(");
	    getChild(0).toFlatzinc(b, false);
	    b.append(",{");
	    ArrayList<Long> setvals=getChild(1).getValueSet();
	    for(int i=0; i<setvals.size(); i++) {
	        b.append(setvals.get(i));
	        if(i<setvals.size()-1) b.append(",");
	    }
	    b.append("});");
	}
	public void toMinizinc(StringBuffer b, boolean bool_context) {
	    b.append("(");
	    getChild(0).toMinizinc(b, false);
	    b.append(" in {");
	    ArrayList<Long> setvals=getChild(1).getValueSet();
	    for(int i=0; i<setvals.size(); i++) {
	        b.append(setvals.get(i));
	        if(i<setvals.size()-1) b.append(",");
	    }
	    b.append("})");
	}
	public void toSAT(Sat satModel) throws IOException {
	    satModel.unaryDirectEncoding(this, getChild(0));
	}
	public void toSATWithAuxVar(Sat satModel, long aux) throws IOException {
	    satModel.unaryDirectEncodingWithAuxVar(this, getChild(0), aux);
	}
	public boolean test(long val) {
	    return getChild(1).containsValue(val);
	}
}