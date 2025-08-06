import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestRegressor
from sklearn.preprocessing import StandardScaler
import joblib
import os

class NutritionPredictor:
    def __init__(self, model_type='random_forest'):
        self.model_type = model_type
        self.models = {}  # Dictionary to store models for each target
        self.scalers = {'features': None, 'targets': {}}
        self.feature_columns = ['type', 'target_weight', 'Body weight (lbs)', 'ADG (lbs)']
        self.target_columns = [
            'DM Intake (lbs/day)', 'TDN (% DM)', 'NEm (Mcal/lb)',
            'NEg (Mcal/lb)', 'CP (% DM)', 'Ca (%DM)', 'P (% DM)',
            'TDN (lbs)', 'NEm (Mcal)', 'NEg (Mcal)', 'CP (lbs)',
            'Ca (grams)', 'P (grams)'
        ]
        
    def _get_model(self):
        if self.model_type == 'random_forest':
            return RandomForestRegressor(n_estimators=100, random_state=42)
        # Add more model types here as needed
        # elif self.model_type == 'polynomial':
        #     return Pipeline([
        #         ('poly', PolynomialFeatures(degree=2)),
        #         ('linear', LinearRegression())
        #     ])
        # elif self.model_type == 'linear':
        #     return LinearRegression()
        else:
            raise ValueError(f"Unknown model type: {self.model_type}")

    def train(self, data_path):
        # Load data
        df = pd.read_csv(data_path)
        
        # Prepare features and targets
        X = df[self.feature_columns]
        
        # Scale features
        self.scalers['features'] = StandardScaler()
        X_scaled = self.scalers['features'].fit_transform(X)
        
        # Train a separate model for each target
        for target in self.target_columns:
            print(f"Training model for {target}")
            y = df[target]
            
            # Scale target
            self.scalers['targets'][target] = StandardScaler()
            y_scaled = self.scalers['targets'][target].fit_transform(y.values.reshape(-1, 1))
            
            # Create and train model
            model = self._get_model()
            model.fit(X_scaled, y_scaled.ravel())
            self.models[target] = model

    def predict(self, type_val, target_weight, body_weight, adg):
        # Create a DataFrame with named columns instead of numpy array
        print(f"Tool call: ", type_val, target_weight, body_weight, adg)
        X = pd.DataFrame([[type_val, target_weight, body_weight, adg]], 
                        columns=self.feature_columns)
        
        # Scale features
        X_scaled = self.scalers['features'].transform(X)
        
        # Make predictions for each target
        predictions = {}
        for target in self.target_columns:
            pred_scaled = self.models[target].predict(X_scaled)
            pred = self.scalers['targets'][target].inverse_transform(pred_scaled.reshape(-1, 1))
            predictions[target] = pred[0][0]
            
        # print(predictions.keys())  
        for key, value in predictions.items():
            print(f"{key}: {value:.3f}")
        return predictions

    def save_models(self, directory='saved_models'):
        # Create directory if it doesn't exist
        os.makedirs(directory, exist_ok=True)
        
        # Save models and scalers
        joblib.dump(self.models, os.path.join(directory, 'models.joblib'))
        joblib.dump(self.scalers, os.path.join(directory, 'scalers.joblib'))
        
    def load_models(self, directory='saved_models'):
        # Load models and scalers
        self.models = joblib.load(os.path.join(directory, 'models.joblib'))
        self.scalers = joblib.load(os.path.join(directory, 'scalers.joblib'))

# Example usage
if __name__ == "__main__":
    # Create and train the model
    predictor = NutritionPredictor(model_type='random_forest')
    # predictor.train('combined_growing.csv')
    
    # Save the models
    predictor.load_models()
    
    # Example prediction
    print("\nExample prediction:")
    prediction = predictor.predict(
        type_val=2,  # growing_yearlings
        target_weight=2000.0,
        body_weight=1050.0,
        adg=0.5
    )
    
    # Print predictions in a formatted way
    print("\nPredicted nutrition requirements:")
    for target, value in prediction.items():
        print(f"{target}: {value:.3f}")