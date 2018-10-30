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
import savilerow.model.*;
import savilerow.treetransformer.*;

public class ExistsExpression extends Quantifier
{
    public static final long serialVersionUID = 1L;
    public ExistsExpression(ASTNode i, ASTNode d, ASTNode e) {
        super(i,d,e);
    }
    
	public ASTNode copy()
	{
	    return new ExistsExpression(getChild(0), getChild(1), getChild(2));
	}
	public boolean isRelation(){return true;}
	
	public ASTNode simplify() {
	    /*
	    // Is this correct? What if domain is empty?
	    if(! getChild(2).contains(getChild(0)) && !getChild(3).contains(getChild(0))) {
	        // Identifier not used anywhere.
	        return new And(getChild(3), getChild(2));
	    }*/
	    
	    if(getChild(1) instanceof SimpleDomain && getChild(1).getCategory()==ASTNode.Constant
	        && !CmdFlags.getClasstrans() ) {
	        
	        // If the domain is large, don't unroll.
	        Intpair dombnds=getChild(1).getBounds();
	        if(dombnds.lower+100 >= dombnds.upper) {
	            // Unroll it. This is supposed to be an optimisation to speed up TransformQuantifiedExpression by helping
	            // TQE to simplify while unrolling.
                ArrayList<Long> qvals=getChild(1).getValueSet();
                ArrayList<ASTNode> expansion=new ArrayList<ASTNode>();
                for(int i=0; i<qvals.size(); i++) 
                {
                    ASTNode unfoldedExpression=getChild(2).copy();
                    
                    // Put in the condition.
                    if(! getChild(3).equals(new BooleanConstant(true))) {
                        unfoldedExpression=new And(getChild(3), unfoldedExpression);
                    }
                    
                    // Sub in the value. 
                    ReplaceASTNode t=new ReplaceASTNode(getChild(0), new NumberConstant(qvals.get(i)));
                    unfoldedExpression=t.transform(unfoldedExpression);
                    
                    expansion.add(unfoldedExpression);
                }
                
                return new Or(expansion);
            }
	    }
	    
	    // Should check for unit domains here.
	    
	    /*if(getChild(2) instanceof ExistsExpression) {
	        ArrayList<ASTNode> newidlist=getChild(0).getChildren();
	        newidlist.addAll(getChild(2).getChild(0).getChildren());
	        setChild(0, new Container(newidlist));
	        
	        ArrayList<ASTNode> newdomlist=getChild(1).getChildren();
	        newdomlist.addAll(getChild(2).getChild(1).getChildren());
	        setChild(1, new Container(newdomlist));
	        setChild(2, getChild(2).getChild(2));
	    }*/
	    return this;
	}
	
	public String toString() {
	    return "(exists "+getChild(0)+" : "+getChild(1)+" . "+getChild(2)+")";
	}
	
	public void toDominionParam(StringBuffer b) {
	    b.append("Max([");
	    getChild(2).toDominionParam(b);
	    b.append("|");
	    
	    //ArrayList<ASTNode> ids=getChild(0).getChildren();
	    //for(int i=0; i<ids.size(); i++) {
	    getChild(0).toDominionParam(b);
	        //ids.get(i).toDominionParam(b);
	        b.append(" in {");
	        getChild(1).toDominionParam(b);  // The only change from original ForallExpression.
	        b.append("}");
	        //if(i<ids.size()-1) b.append(", ");
	    //}
	    if(getChild(2) instanceof Implies && getChild(2).getChild(0).getCategory()<=ASTNode.Quantifier) {
	        b.append(", ");
	        ASTNode c=getChild(2).getChild(0);
	        if(c instanceof And) {
	            for(int i=0; i<c.numChildren(); i++) {
	                c.getChild(i).toDominionParam(b);
	                if(i<c.numChildren()-1) b.append(", ");
	            }
	        }
	        else {
	            getChild(2).getChild(0).toDominionParam(b);  // output the single condition.
	        }
	    }
	    b.append("])");
	}
	public void toDominionInner(StringBuffer b, boolean bool_context) {
	    // depends on context. If it's a parameter expression inside, for example, a domain, then it should probably be written as a function.
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("or([");
	    ArrayList<ASTNode> conditions=new ArrayList<ASTNode>();
	    
	    if(getChild(2) instanceof And) {
	        // Split it into conditions (quantifier expressions) and constraints
	        ArrayList<ASTNode> nonconditions=new ArrayList<ASTNode>();
	        for(int i=0; i<getChild(2).numChildren(); i++) {
                if(getChild(2).getChild(i).getCategory()<=ASTNode.Quantifier) {
                    conditions.add(getChild(2).getChild(i).copy());
                }
                else {
                    nonconditions.add(getChild(2).getChild(i).copy());
                }
	        }
	        
	        if(nonconditions.size()>1) {
	            (new And(nonconditions)).toDominion(b, true);
	        }
	        else if(nonconditions.size()==1) {
	            nonconditions.get(0).toDominion(b, true);
	        }
	        
	    }
	    else {
	        getChild(2).toDominion(b, true);
	    }
	    b.append(" | ");
	    //ArrayList<ASTNode> ids=getChild(0).getChildren();
	    //for(int i=0; i<ids.size(); i++) {
	        //ids.get(i).toDominionParam(b);
	        getChild(0).toDominionParam(b);
	        b.append(" in {");
	        getChild(1).toDominionParam(b);  // The only change from original ExistsExpression
	        b.append("}");
	        //if(i<ids.size()-1) b.append(", ");
	    //}
	    if(conditions.size()>0) {
	        b.append(", ");
	        (new And(conditions)).toDominionParam(b);
	    }
	    b.append("])");
	}
}
