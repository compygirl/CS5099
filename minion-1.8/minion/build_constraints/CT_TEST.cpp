#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net
   
   For Licence Information see file LICENSE.txt 
*/

#include "../constraints/constraint_test.h"

template<typename VarArray>
AbstractConstraint*
BuildCT_TEST(StateObj* stateObj, const VarArray& _var_array, ConstraintBlob&)
{ return (new TestConstraint<VarArray>(stateObj, _var_array)); }

BUILD_CT(CT_TEST, 1)
