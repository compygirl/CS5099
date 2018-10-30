package savilerow.treetransformer;
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


import savilerow.expression.*;
import savilerow.model.*;

//  Transform equality with a constant/single identifier/matrixderef[constants] into a tovariable, before flattening.
// It's a special-case flattener.
// Works when one side of the equality needs to be flattened, and the other side is atomic, and does the flattening without an aux variable.

public class TransformEqualConst extends TreeTransformerBottomUpNoWrapper
{
    public TransformEqualConst() {
        super(null);
    }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof Equals) {
            // Equals can contain boolean and non-boolean expressions. 
            // ToVariable is special cased to reify when 
            
            // If one side (a constraint) is boolean and the other (constant or identifier) is not,  need to stay as an equals.
            
            ASTNode lc=curnode.getChild(0);
            ASTNode rc=curnode.getChild(1);
            
            // lc id, rc constraint
            Intpair plc=lc.getBounds();
            if(lc.isRelation() || (plc.lower==0 && plc.upper==1)) {
                NodeReplacement r1=do_flatten(lc,rc);
                if(r1!=null) return r1;
            }
            
            // lc constraint, rc id
            Intpair prc=rc.getBounds();
            if(rc.isRelation() || (prc.lower==0 && prc.upper==1)) {
                NodeReplacement r1=do_flatten(rc,lc);   // Try the other way. 
                if(r1!=null) return r1;
            }
        }
        
        if(curnode instanceof Iff) {
            // Subtly different semantics to Equals. Iff forces both arguments to be
            // boolean.
            assert curnode.getChild(0).isRelation() && curnode.getChild(1).isRelation();
            
            ASTNode lc=curnode.getChild(0);
            ASTNode rc=curnode.getChild(1);
            
            NodeReplacement r1=do_flatten(lc,rc);
            if(r1==null) {
                r1=do_flatten(rc,lc);   // Try the other way. 
            }
            if(r1!=null) return r1;
        }
        
        // Bool expressions in a and/or/negate are treated like equalities with 1
        // Need to say "in a logical connective" but that's hard to do.
        // Currently only works on Element
        if(curnode instanceof Element) {
            if(curnode.getParent()!=null && (curnode.getParent() instanceof And || curnode.getParent() instanceof Or || curnode.getParent() instanceof Negate) ) {
                return do_flatten(new BooleanConstant(true), curnode);
            }
        }
        
        // What about SafeElement? Not needed with deletevars. 
        return null;
    }
    
    private NodeReplacement do_flatten(ASTNode c1, ASTNode c2) {
        if(c2.toFlatten(false)) {
            if(c1.isConstant() || c1 instanceof Identifier) {
                return new NodeReplacement(new ToVariable(c2, c1));
            }
            if(c1 instanceof MatrixDeref || c1 instanceof SafeMatrixDeref) {
                boolean constindices=true;   // See if the matrixderef has only constant indices.
                for(int i=1; i<c1.numChildren(); i++) if(c1.getChild(i).getCategory()>ASTNode.Parameter) constindices=false;
                if(constindices) {
                    return new NodeReplacement(new ToVariable(c2, c1));
                }
            }
        }
        return null;
    }
}
