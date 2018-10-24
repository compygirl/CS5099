#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net
   
   For Licence Information see file LICENSE.txt 
*/

#include "../constraints/constraint_gcc.h"

template<typename VarArray1, typename VarArray2>
AbstractConstraint*
BuildCT_GCC(StateObj* stateObj, const VarArray1& var_array, const VarArray2& cap_array, ConstraintBlob& b)
{ return new GCC<VarArray1, VarArray2, true>(stateObj, var_array, cap_array, b.constants[0]); }

BUILD_CT(CT_GCC, 2)
