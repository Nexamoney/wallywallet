import Foundation
import SwiftUI
import src

struct ComposeContentView: UIViewControllerRepresentable
{
    func updateUIViewController(_ updateUIViewController: UIViewControllerType, context: Context)
    {}
    func makeUIViewController(context: Context) -> some UIViewController {
        MainViewControllerKt.MainViewController()
    }
}

struct ContentView: View {
	let greet = "hi" // Greeting().greet()

	var body: some View {
	    ComposeContentView()
		//Text(greet)
	}
}

struct ContentView_Previews: PreviewProvider {
	static var previews: some View {
		ContentView()
	}
}