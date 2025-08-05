import os
from google import genai
from nutrition_model import NutritionPredictor

class CattleNutritionAdvisor:
    def __init__(self, api_key: str):
        """
        Initialize the Cattle Nutrition Advisor system
        
        Args:
            api_key: Google AI API key
        """
        self.client = genai.Client(api_key=api_key)
        self.model_name = "gemma-3n-e4b-it"
        self.predictor = NutritionPredictor()
        self.predictor.load_models('saved_models')
        
        # Cattle type mapping
        self.type_mapping = {
            "growing_steer_heiver": 0,
            "growing_yearlings": 1,
            "growing_mature_bulls": 2
        }
        
        # Validation rules
        self.validation_rules = {
            "growing_mature_bulls": {
                "max_target_weight": 2300
            },
            "growing_steer_heiver": {
                "max_target_weight": 1400
            },
            "growing_yearlings": {
                "max_target_weight": 1400
            }
        }
        
        # Feed ingredients database
        self.feed_ingredients = {
            "Alfalfa Hay": {"TDN": 58, "NEm": 0.50, "NEg": 0.30, "CP": 17, "Ca": 1.20, "P": 0.22},
            "Corn Silage": {"TDN": 65, "NEm": 0.60, "NEg": 0.35, "CP": 8, "Ca": 0.30, "P": 0.22},
            "Soybean Meal (48%)": {"TDN": 82, "NEm": 0.70, "NEg": 0.40, "CP": 48, "Ca": 0.30, "P": 0.65},
            "Ground Corn": {"TDN": 88, "NEm": 0.90, "NEg": 0.65, "CP": 9, "Ca": 0.02, "P": 0.28},
            "Dicalcium Phosphate": {"TDN": 0, "NEm": 0, "NEg": 0, "CP": 0, "Ca": 23.00, "P": 18.00},
            "Trace Mineral Mix": {"TDN": 0, "NEm": 0, "NEg": 0, "CP": 0, "Ca": 12.00, "P": 8.00},
            "Salt": {"TDN": 0, "NEm": 0, "NEg": 0, "CP": 0, "Ca": 0.00, "P": 0.00}
        }
        
    def _validate_input(self, cattle_type: str, target_weight: float) -> tuple[bool, str]:
        """Validate input parameters"""
        if cattle_type not in self.validation_rules:
            return False, f"Invalid cattle type. Must be one of: {', '.join(self.validation_rules.keys())}"
        
        max_weight = self.validation_rules[cattle_type]["max_target_weight"]
        if target_weight > max_weight:
            return False, f"Target weight for {cattle_type} should not exceed {max_weight} lbs. Please consult an expert for higher weights."
        
        return True, ""

    def get_nutrition_prediction(self, cattle_type: str, target_weight: float, 
                               body_weight: float, adg: float) -> dict:
        """Get nutrition predictions from the model"""
        # Validate inputs
        is_valid, error_msg = self._validate_input(cattle_type, target_weight)
        if not is_valid:
            raise ValueError(error_msg)
        
        # Get predictions
        type_val = self.type_mapping[cattle_type]
        predictions = self.predictor.predict(
            type_val=type_val,
            target_weight=target_weight,
            body_weight=body_weight,
            adg=adg
        )
        
        return predictions

    def generate_feed_recommendation(self, predictions: dict, unavailable_ingredients: list = None) -> str:
        """Generate feed recommendations based on nutrition predictions"""
        
        # Create the base prompt with nutrition requirements
        prompt = f"""You are an expert cattle nutritionist.

A cow needs the following nutrients per day:
- Dry Matter Intake (DMI): {predictions.get('DM Intake (lbs/day)', 0):.1f} lbs
- Total Digestible Nutrients (TDN): {predictions.get('TDN (% DM)', 0):.1f}% of DM ({predictions.get('TDN (lbs)', 0):.1f} lbs)
- Net Energy for Maintenance (NEm): {predictions.get('NEm (Mcal/lb)', 0):.2f} Mcal/lb ({predictions.get('NEm (Mcal)', 0):.1f} Mcal)
- Net Energy for Gain (NEg): {predictions.get('NEg (Mcal/lb)', 0):.2f} Mcal/lb ({predictions.get('NEg (Mcal)', 0):.1f} Mcal)
- Crude Protein (CP): {predictions.get('CP (% DM)', 0):.1f}% of DM ({predictions.get('CP (lbs)', 0):.2f} lbs)
- Calcium (Ca): {predictions.get('Ca (%DM)', 0):.2f}% of DM ({predictions.get('Ca (grams)', 0):.0f} g)
- Phosphorus (P): {predictions.get('P (% DM)', 0):.2f}% of DM ({predictions.get('P (grams)', 0):.0f} g)

Here is a list of available feed ingredients and their nutrient values per pound of dry matter:

| Feed Ingredient        | TDN (%) | NEm (Mcal/lb) | NEg (Mcal/lb) | CP (%) | Ca (%) | P (%) |
|------------------------|---------|----------------|----------------|--------|--------|--------|"""

        # Add available ingredients to the prompt
        available_ingredients = self.feed_ingredients.copy()
        if unavailable_ingredients:
            for ingredient in unavailable_ingredients:
                available_ingredients.pop(ingredient, None)
        
        for name, values in available_ingredients.items():
            prompt += f"\n| {name:<20} | {values['TDN']:<7} | {values['NEm']:<12} | {values['NEg']:<12} | {values['CP']:<6} | {values['Ca']:<6} | {values['P']:<6} |"

        prompt += f"""

**Your Task:**
- Design a realistic daily feed menu of 5 to 7 ingredients from the available ingredients.
- Show quantity of each ingredient in pounds of dry matter.
- Calculate and show the contribution of each to total TDN, NEm, NEg, CP, Ca, and P.
- Ensure the totals are as close as possible to the cow's requirements above.
- Keep the ingredients reasonable and commonly used."""

        if unavailable_ingredients:
            prompt += f"\n\nNote: The following ingredients are not available: {', '.join(unavailable_ingredients)}. Please adjust the feed menu accordingly."

        prompt += f"""

Return a table like this:

| Ingredient            | Amount (lbs DM) | TDN (lbs) | NEm (Mcal) | NEg (Mcal) | CP (lbs) | Ca (g) | P (g) |
|-----------------------|------------------|------------|-------------|-------------|----------|--------|--------|
| Feed 1                |                  |            |             |             |          |        |        |
| ...                   |                  |            |             |             |          |        |        |
| **Total**             | {predictions.get('DM Intake (lbs/day)', 0):.1f} | {predictions.get('TDN (lbs)', 0):.1f} | {predictions.get('NEm (Mcal)', 0):.1f} | {predictions.get('NEg (Mcal)', 0):.1f} | {predictions.get('CP (lbs)', 0):.2f} | {predictions.get('Ca (grams)', 0):.0f} | {predictions.get('P (grams)', 0):.0f} |

After your table, list any assumptions or notes you made.

Start your response with: "Here is the feed menu that meets the cow's nutrient needs."
"""

        try:
            response = self.client.models.generate_content(
                model=self.model_name,
                contents=[{"role": "user", "parts": [{"text": prompt}]}]
            )
            return response.text
        except Exception as e:
            return f"Error generating feed recommendation: {str(e)}"


def main():
    # Configuration
    API_KEY = "AIzaSyA068ieqaXFEYC4VLJLolgyPotX1e-9w1E"  # Your API key
    
    try:
        # Initialize advisor
        print("Initializing Cattle Nutrition Advisor...")
        advisor = CattleNutritionAdvisor(api_key=API_KEY)
        
        print("\nCattle Nutrition Advisor ready! Let's help you determine the proper nutrition for your cattle.")
        print("Please provide the following information:")
        
        # Get initial cattle information
        cattle_type = input("\nCattle type (growing_steer_heiver/growing_yearlings/growing_mature_bulls): ").strip()
        target_weight = float(input("Target weight (lbs): ").strip())
        body_weight = float(input("Current body weight (lbs): ").strip())
        adg = float(input("Average Daily Gain - ADG (lbs): ").strip())
        
        try:
            # Get nutrition predictions
            predictions = advisor.get_nutrition_prediction(
                cattle_type=cattle_type,
                target_weight=target_weight,
                body_weight=body_weight,
                adg=adg
            )
            
            # Generate initial feed recommendation
            print("\nGenerating feed recommendation...")
            recommendation = advisor.generate_feed_recommendation(predictions)
            print("\n" + recommendation)
            
            # Interactive loop for ingredient adjustments
            while True:
                print("\nWould you like to adjust the feed menu? You can:")
                print("1. Remove unavailable ingredients")
                print("2. Exit")
                
                choice = input("\nYour choice (1/2): ").strip()
                
                if choice == "2":
                    break
                elif choice == "1":
                    unavailable = input("\nEnter unavailable ingredients (comma-separated): ").strip()
                    if unavailable:
                        unavailable_list = [ing.strip() for ing in unavailable.split(",")]
                        print("\nGenerating new feed recommendation...")
                        recommendation = advisor.generate_feed_recommendation(predictions, unavailable_list)
                        print("\n" + recommendation)
                else:
                    print("Invalid choice. Please try again.")
        
        except ValueError as e:
            print(f"\nError: {e}")
        except Exception as e:
            print(f"\nUnexpected error: {e}")
    
    except Exception as e:
        print(f"Error initializing advisor: {e}")


if __name__ == "__main__":
    main()
