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

public class TransformEqualConstClass extends TreeTransformerBottomUpNoWrapper
{
    public TransformEqualConstClass() {
        super(null);
    }
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof Equals || curnode instanceof Iff)
        {
            ASTNode lc=curnode.getChild(0);
            ASTNode rc=curnode.getChild(1);
            
            NodeReplacement r1=do_flatten(lc,rc);
            if(r1==null) {
                return do_flatten(rc,lc);
            }
        }
        return null;
    }
    
    private NodeReplacement do_flatten(ASTNode c1, ASTNode c2) {
        if(c2.toFlatten(false) && c2.getCategory()>ASTNode.Quantifier ) {
            if(c1.getCategory() <=ASTNode.Quantifier || c1 instanceof Identifier) {
                return new NodeReplacement(new ToVariable(c2, c1));
            }
            if(c1 instanceof MatrixDeref || c1 instanceof SafeMatrixDeref) {
                boolean constindices=true;   // See if the matrixderef has only constant/parameter/quantifier indices.
                for(int i=1; i<c1.numChildren(); i++) if(c1.getChild(i).getCategory()>ASTNode.Quantifier) constindices=false;
                if(constindices) {
                    return new NodeReplacement(new ToVariable(c2, c1));
                }
            }
        }
        return null;
    }
}
