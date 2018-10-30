class Variable():
    def __init__(self, vid, value, domain):
        self.id = vid
        self.int_id = self.id[0] * 9 + self.id[1]
        self.value = value
        self.domain = domain
        self.static_degree = 0
        self.future_degree = 0
        self.heuristic_value = self.int_id

    def change_value(self, new_value):
        self.value = new_value
        
    def force_value(self, new_value):
        self.value = new_value
        self.domain = []
        self.domain.append(new_value)

    def __str__(self):
        return str(self.value)

    def __lt__(self, other_var):
        return self.heuristic_value < other_var.heuristic_value

    def __gt__(self, other_var):
        return self.heuristic_value > other_var.heuristic_value
