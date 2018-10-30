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


import java.util.*;
import savilerow.model.SymbolTable;
import savilerow.CmdFlags;

public class HoleyMatrixDomain extends Domain
{
    public static final long serialVersionUID = 1L;
    // First child is a MatrixDomain with infinite indices and an infinite (or bool) base domain.
    // Second child is a forall-find. 
    // This just exists until matrices are demolished. 
    
    public HoleyMatrixDomain(ASTNode matdom, ASTNode forallfind) {
        super(matdom, forallfind);
    }
    
	public ASTNode copy() {
	    return new HoleyMatrixDomain(getChild(0), getChild(1));
	}
	
	public PairASTNode getBoundsAST() {
	    return new PairASTNode(new NegInfinity(), new PosInfinity()); // Can't know what bounds are in general.
	}
}
