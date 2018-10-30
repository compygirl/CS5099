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
import java.lang.Math;
import savilerow.model.SymbolTable;
import savilerow.treetransformer.*;

public class MultiplyMapper extends ASTNode
{
    public static final long serialVersionUID = 1L;
	public MultiplyMapper(ASTNode a, ASTNode mult) {
	    super(a, mult);
	}
	
	public ASTNode copy()
	{
	    return new MultiplyMapper(getChild(0), getChild(1));
	}
	
	public boolean toFlatten(boolean propagate) { return false;}  // Never flatten this. 
	
    public ASTNode simplify() {
        assert getChild(0).getCategory()==ASTNode.Decision || getChild(0).getCategory()==ASTNode.Undeclared;  // allows it to work on detached bits of trees.
        assert getChild(1).getCategory()<=ASTNode.Quantifier || getChild(1).getCategory()==ASTNode.Undeclared;
        
        
        if(getChild(1).equals(new NumberConstant(1))) {
            return getChild(0);
        }
        if(getChild(1).equals(new NumberConstant(0))) {
            return new NumberConstant(0);
        }
        
        if(getChild(0) instanceof MultiplyMapper) {
            // Put the two multiplymappers together.
            ASTNode newmul=BinOp.makeBinOp("*", getChild(1), getChild(0).getChild(1));
            return new MultiplyMapper(getChild(0).getChild(0), newmul);
        }
        return this;
    }
    
	public Intpair getBounds()
	{
	    Intpair a=getChild(0).getBounds();
	    for(int i=1; i<numChildren(); i++) {
            Intpair b=getChild(i).getBounds();
            // multiply the four combinations of bounds
            long w=Times.multiply(a.lower, b.lower);
            long x=Times.multiply(a.upper, b.lower);
            long y=Times.multiply(a.lower, b.upper);
            long z=Times.multiply(a.upper, b.upper);
            a.lower=Math.min(w, Math.min(x, Math.min(y,z)));
            a.upper=Math.max(w, Math.max(x, Math.max(y,z)));
	    }
	    return lookupBounds(a);    //  Look up in FilteredDomainStore
	}
	
    public PairASTNode getBoundsAST() 
	{
	    PairASTNode a=getChild(0).getBoundsAST();
	    for(int i=1; i<numChildren(); i++) {
            PairASTNode b=getChild(i).getBoundsAST();
            // multiply the four combinations of bounds
            ASTNode w=new Times(a.e1, b.e1);
            ASTNode x=new Times(a.e2, b.e1);
            ASTNode y=new Times(a.e1, b.e2);
            ASTNode z=new Times(a.e2, b.e2);
            
            ArrayList<ASTNode> ls=new ArrayList<ASTNode>();
            ls.add(w); ls.add(x); ls.add(y); ls.add(z);
            a.e1=new Min(ls);
            a.e2=new Max(ls);   // will make their own internal copies of ls
        }
        return a;
	}
    
	public String toString() {
	    return "mult("+getChild(0)+", "+getChild(1)+")";
	}
	public void toDominionInner(StringBuffer b, boolean bool_context) {
	    if(getChild(1).equals(new NumberConstant(-1))) {
	        b.append("neg(");
	        getChild(0).toDominion(b, false);
	        b.append(")");
	        return;
	    }
	    
	    PairASTNode p=getChild(1).getBoundsAST();
	    TransformSimplify ts=new TransformSimplify();
	    p.e1=ts.transform(p.e1);
	    if(p.e1.isConstant() && p.e1.getValue()>=0) {
	        b.append("posmult(");
            getChild(0).toDominion(b, false);
            b.append(",");
            getChild(1).toDominion(b, false);
            b.append(")");
            return;
	    }
	    
	    b.append("mult(");
        getChild(0).toDominion(b, false);
        b.append(",");
        getChild(1).toDominion(b, false);
        b.append(")");
	    
	}
}
