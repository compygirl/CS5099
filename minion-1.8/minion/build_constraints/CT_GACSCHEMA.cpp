#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net
   
   For Licence Information see file LICENSE.txt 
*/

#include "../constraints/constraint_gacschema.h"

template<typename VarArray>
AbstractConstraint*
BuildCT_GACSCHEMA(StateObj* stateObj, const VarArray& var_array, ConstraintBlob& b)
{ return new GACSchema<VarArray>(stateObj, var_array, b.tuples); }

BUILD_CT(CT_GACSCHEMA, 1)
