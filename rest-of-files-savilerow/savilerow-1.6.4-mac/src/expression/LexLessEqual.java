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
import savilerow.model.*;
import java.util.*;

public class LexLessEqual extends MatrixBinOp
{
    public static final long serialVersionUID = 1L;
	public LexLessEqual(ASTNode l, ASTNode r)
	{
		super(l, r);
	}
	
	public ASTNode copy()
	{
	    return new LexLessEqual(getChild(0), getChild(1));
	}
	public boolean isRelation(){return true;}
	
	@Override
	public ASTNode simplify() {
	    if( (getChild(0) instanceof CompoundMatrix || getChild(0) instanceof EmptyMatrix) && 
	        (getChild(1) instanceof CompoundMatrix || getChild(1) instanceof EmptyMatrix) ) {
	        ArrayList<ASTNode> left=getChild(0).getChildren(); left.remove(0);
	        ArrayList<ASTNode> right=getChild(1).getChildren(); right.remove(0);
	        
	        // Deal with unequal length first.
	        if(left.size()<right.size()) {
	            // If they are equal up to the size of left, that will satisfy the constraint. 
	            
	            while(right.size()>left.size()) right.remove(right.size()-1);  // Trim to same size. 
	            
	            return new LexLessEqual(CompoundMatrix.makeCompoundMatrix(left), CompoundMatrix.makeCompoundMatrix(right));
	        }
	        if(left.size()>right.size()) {
	            // If they are equal up to the size of right, that will violate the constraint.
	            // Rewrite into a LexLess.
	            while(left.size()>right.size()) left.remove(left.size()-1);  // Trim to same size.
	            
	            return new LexLess(CompoundMatrix.makeCompoundMatrix(left), CompoundMatrix.makeCompoundMatrix(right));
	        }
	        
	        boolean changed=false;
	        
	        // Now trim out all equal entries. 
            int location = 0;
            while(location < left.size()) {
                if(left.get(location).equals(right.get(location))) {
                    left.remove(location);
                    right.remove(location);
                    changed=true;
                }
                else {
                    location++;
                }
            }
            
            changed = changed || furtherSimplify(left, right);
            
	        if(changed) return new LexLessEqual(CompoundMatrix.makeCompoundMatrix(left), CompoundMatrix.makeCompoundMatrix(right));
	        
	        // Trim from the right-hand side. 
	        while(left.size()>=1) {
	            Intpair leftbnds=left.get(left.size()-1).getBounds();
	            Intpair rightbnds=right.get(right.size()-1).getBounds();
	            
	            // To satisfy the LexLessEqual constraint, the rightmost element must
	            // be less or equal.  Trim off ones that are greater. 
	            if(leftbnds.lower > rightbnds.upper) {
	                left.remove(left.size()-1);
	                right.remove(right.size()-1);
	                changed=true;
	            }
	            else {
	                break;
	            }
	        }
	        
	        if(changed) {
	            // If changed by trimming the right-hand side, the two vectors cannot be
	            // equal. Rewrite to a strict less-than. 
	            return new LexLess(CompoundMatrix.makeCompoundMatrix(left), CompoundMatrix.makeCompoundMatrix(right));
	        }
	        
	        if(left.size()==0 && right.size()==0) {
	            // Since this is less-equal, the constraint is satisfied. 
	            return new BooleanConstant(true);
	        }
	        
	        // Finally, check if the leftmost elements are disjoint and lessthan. 
	        
	        Intpair leftbnds=left.get(0).getBounds();
	        Intpair rightbnds=right.get(0).getBounds();
	        
	        if(leftbnds.upper<rightbnds.lower) {
	            // Guaranteed satisfied by the first element. 
	            return new BooleanConstant(true);
	        }
	        
	        if(leftbnds.lower>rightbnds.upper) {
	            // Guaranteed violated by first element. 
	            return new BooleanConstant(false);
	        }
	        
	        // Strength reduction to numerical <=
	        if(left.size()==1) {
	            return new LessEqual(left.get(0), right.get(0));
	        }
	        
	        if(changed) return new LexLessEqual(CompoundMatrix.makeCompoundMatrix(left), CompoundMatrix.makeCompoundMatrix(right));
	    }
	    return this;
	}
	
	public boolean furtherSimplify(ArrayList<ASTNode> left, ArrayList<ASTNode> right) {
	    if (left.size() != right.size()) {
	        throw new IllegalArgumentException("Expecting two ArrayLists of the same length");
	    }
	    boolean changed = false;
	    Map<ASTNode, Set<ASTNode>> containingSets = new HashMap<ASTNode, Set<ASTNode>>(); // mapping from variable to the set containing that variable
	    
	    int i = 0;
	    
	    while (i < left.size()) {
	        Set<ASTNode> leftContainer  = containingSets.get(left.get(i));
	        Set<ASTNode> rightContainer = containingSets.get(right.get(i));
	        if (leftContainer == null) {
	            leftContainer = new HashSet<ASTNode>();
	            leftContainer.add(left.get(i));
	            containingSets.put(left.get(i), leftContainer);
	        }
	        if (rightContainer == null) {
	            rightContainer = new HashSet<ASTNode>();
	            rightContainer.add(right.get(i));
	            containingSets.put(right.get(i), rightContainer);
	        }

	        if (leftContainer.contains(right.get(i)) || rightContainer.contains(left.get(i))) {
	            left.remove(i);
	            right.remove(i);
	            changed = true;
	        } else {
	            //moving always happens left to right so swap references if necessary 
	            if (leftContainer.size() > rightContainer.size()) {
	                //swap sets
	                Set<ASTNode> tempContainer = leftContainer;
	                leftContainer = rightContainer;
	                rightContainer = tempContainer;
	            }

	            //move all from leftset to right set
	            rightContainer.addAll(leftContainer);
	            for (ASTNode n: leftContainer) {
	                containingSets.put(n, rightContainer);
	            }
	            i++;
	        }
	    }
	    return changed;
	}
	
	public void toMinion(StringBuffer b, boolean bool_context)
	{
	    assert bool_context;
	    b.append("lexleq(");
	    getChild(0).toMinion(b, false);
	    b.append(",");
	    getChild(1).toMinion(b, false);
	    b.append(")");
	}
	public void toDominionInner(StringBuffer b, boolean bool_context)
	{
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("lexleq(flatten(");
	    getChild(0).toDominion(b, false);
	    b.append("),flatten(");
	    getChild(1).toDominion(b, false);
	    b.append("))");
	}
	public String toString() {
	    return "("+getChild(0)+" <=lex "+getChild(1)+")";
	}
	/*  Experiment with the boolean lex ordering constraint. 
	public void toFlatzinc(StringBuffer b, boolean bool_context)
	{
	    boolean boolmatrices=getChild(0).isRelation() && getChild(1).isRelation();
	    if(boolmatrices) {
	        b.append("constraint array_bool_lq(");
	    }
	    else {
	        b.append("constraint array_int_lq(");
	    }
	    getChild(0).toFlatzinc(b, boolmatrices);
	    b.append(",");
	    getChild(1).toFlatzinc(b, boolmatrices);
	    b.append(");");
	}*/
	public void toFlatzinc(StringBuffer b, boolean bool_context)
	{
	    b.append("constraint array_int_lq(");
	    getChild(0).toFlatzinc(b, false);
	    b.append(",");
	    getChild(1).toFlatzinc(b, false);
	    b.append(");");
	}
	public void toMinizinc(StringBuffer b, boolean bool_context)
	{
	    b.append("lex_lesseq(");
	    getChild(0).toMinizinc(b, false);
	    b.append(",");
	    getChild(1).toMinizinc(b, false);
	    b.append(")");
	}
}