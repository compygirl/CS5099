#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net
   
   For Licence Information see file LICENSE.txt 
*/

#include "../dynamic_constraints/dynamic_quicklex.h"

template<typename VarArray1, typename VarArray2>
AbstractConstraint*
BuildCT_QUICK_LEXLESS(StateObj* stateObj, const VarArray1& x, const VarArray2& y, ConstraintBlob&)
{ return new QuickLexDynamic<VarArray1, VarArray2, true>(stateObj,x,y); }

BUILD_CT(CT_QUICK_LEXLESS, 2)
